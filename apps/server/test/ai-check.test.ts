import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { checkPrompt, parseVerdict } from '../src/learn/ai-check';
import { ORIGIN, register, sessionCookie, testApp } from './helpers';
import { json, mockServer, openAiStream, stream } from './mock-ai';

describe('KI-Urteil lesen', () => {
  it('versteht genau die drei Wörter (auch mit Satzzeichen/Englisch)', () => {
    expect(parseVerdict('richtig')).toBe('correct');
    expect(parseVerdict(' Richtig.')).toBe('correct');
    expect(parseVerdict('Tippfehler')).toBe('typo');
    expect(parseVerdict('falsch!')).toBe('wrong');
    expect(parseVerdict('correct')).toBe('correct');
    expect(parseVerdict('Die Antwort ist richtig')).toBeNull();
    expect(parseVerdict('')).toBeNull();
  });

  it('baut einen kurzen Prompt mit Richtung, Wort, Lösung und Antwort', () => {
    expect(
      checkPrompt({
        language: 'la',
        direction: 'foreign_native',
        prompt: 'amicus',
        solution: 'Freund',
        answer: 'Kumpel',
      }),
    ).toBe('Richtung: Latein → Deutsch\nWort: amicus\nLösung: Freund\nAntwort: Kumpel');
  });
});

describe('KI-Antwortprüfung im Lernen', () => {
  let app: FastifyInstance;
  let headers: Record<string, string>;
  let mock: Awaited<ReturnType<typeof mockServer>>;
  let verdict = 'richtig';
  let delayMs = 0;

  beforeEach(async () => {
    verdict = 'richtig';
    delayMs = 0;
    mock = await mockServer({
      'GET /v1/models': (_req, res) => json(res, 200, { data: [{ id: 'checker' }] }),
      'POST /v1/chat/completions': async (_req, res) => {
        if (delayMs) await new Promise((r) => setTimeout(r, delayMs));
        await stream(res, 'text/event-stream', openAiStream(verdict, 1));
      },
    });
    app = await testApp();
    headers = { origin: ORIGIN, cookie: `gero_session=${sessionCookie(await register(app))}` };
    const provider = (
      await app.inject({
        method: 'POST',
        url: '/api/ai/providers',
        headers,
        payload: { kind: 'custom', baseUrl: `${mock.url}/v1` },
      })
    ).json().provider;
    await app.inject({
      method: 'PUT',
      url: '/api/ai/tasks/check',
      headers,
      payload: { providerId: provider.id, model: 'checker', reasoning: false },
    });
  });
  afterEach(async () => {
    await app.close();
    await mock.close();
  });

  async function word() {
    const pkg = (
      await app.inject({
        method: 'POST',
        url: '/api/packages',
        headers,
        payload: { name: 'L', language: 'la' },
      })
    ).json().package;
    const [v] = (
      await app.inject({
        method: 'POST',
        url: `/api/packages/${pkg.id}/vocab`,
        headers,
        payload: { items: [{ word: 'amicus', translation: 'Freund' }] },
      })
    ).json().vocab;
    await app.inject({ method: 'POST', url: `/api/packages/${pkg.id}/activate`, headers });
    return v.id as number;
  }

  const answer = (vocabId: number, text: string) =>
    app.inject({
      method: 'POST',
      url: '/api/learn/answer',
      headers,
      payload: { vocabId, direction: 'foreign_native', answer: text },
    });

  const chatCalls = () => mock.requests.filter((r) => r.url === '/v1/chat/completions');

  it('fragt die KI nur bei unklaren Antworten', async () => {
    const id = await word();
    expect((await answer(id, 'Freund')).json().decidedBy).toBe('exact');
    expect((await answer(id, 'Fruend')).json().decidedBy).toBe('typo');
    expect(chatCalls()).toHaveLength(0);
  });

  it('KI sagt richtig: Synonym zählt, kein „Ich hatte recht“', async () => {
    const id = await word();
    const res = (await answer(id, 'Kumpel')).json();
    expect(res).toMatchObject({ correct: true, decidedBy: 'ai', canOverride: false });
    const body = chatCalls()[0]!.body;
    expect(body.model).toBe('checker');
    expect(body.max_tokens).toBe(8);
    expect(body.temperature).toBe(0);
    expect(body.messages[1].content).toContain('Antwort: Kumpel');
  });

  it('KI sagt falsch: falsch, kein „Ich hatte recht“', async () => {
    verdict = 'falsch';
    const id = await word();
    expect((await answer(id, 'Feind')).json()).toMatchObject({
      correct: false,
      decidedBy: 'ai',
      canOverride: false,
    });
  });

  it('merkt sich KI-Urteile je Vokabel und normalisierter Antwort', async () => {
    const id = await word();
    await answer(id, 'Kumpel');
    const again = (await answer(id, '  kumpel! ')).json();
    expect(again).toMatchObject({ correct: true, decidedBy: 'ai' });
    expect(chatCalls()).toHaveLength(1);
  });

  it('Zeitlimit überschritten: lokal falsch, mit „Ich hatte recht“, Latenz als Fehler gezählt', async () => {
    delayMs = 900;
    await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: { aiTimeoutMs: 500 },
    });
    const id = await word();
    const started = Date.now();
    const res = (await answer(id, 'Kumpel')).json();
    expect(Date.now() - started).toBeLessThan(850);
    expect(res).toMatchObject({
      correct: false,
      decidedBy: 'local',
      canOverride: true,
      aiFailed: true,
    });
    const stats = (await app.inject({ method: 'GET', url: '/api/ai/latency', headers })).json()
      .stats;
    expect(stats.find((s: { task: string }) => s.task === 'check')).toMatchObject({
      count: 1,
      failures: 1,
    });
  });

  it('unlesbare KI-Antwort zählt als kein Urteil', async () => {
    verdict = 'Das kommt darauf an';
    const id = await word();
    expect((await answer(id, 'Kumpel')).json()).toMatchObject({
      decidedBy: 'local',
      canOverride: true,
    });
  });

  it('misst die Latenz der Prüfung', async () => {
    const id = await word();
    await answer(id, 'Kumpel');
    const stats = (await app.inject({ method: 'GET', url: '/api/ai/latency', headers })).json()
      .stats;
    const check = stats.find((s: { task: string }) => s.task === 'check');
    expect(check).toMatchObject({ count: 1, failures: 0 });
    expect(check.medianTotalMs).toBeGreaterThanOrEqual(0);
  });

  it('Vorwärmen antwortet sofort', async () => {
    const res = await app.inject({ method: 'POST', url: '/api/learn/warmup', headers });
    expect(res.statusCode).toBe(204);
  });
});
