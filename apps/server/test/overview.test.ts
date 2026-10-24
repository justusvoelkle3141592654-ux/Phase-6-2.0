import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { adminHeaders, ORIGIN, register, sessionCookie, testApp } from './helpers';
import { json, mockServer, openAiStream, stream } from './mock-ai';

let app: FastifyInstance;
let headers: Record<string, string>;
let mock: Awaited<ReturnType<typeof mockServer>>;
let fail = false;

beforeEach(async () => {
  fail = false;
  mock = await mockServer({
    'GET /v1/models': (_req, res) => json(res, 200, { data: [] }),
    'POST /v1/chat/completions': (_req, res) =>
      fail
        ? json(res, 500, { error: { message: 'kaputt' } })
        : stream(res, 'text/event-stream', openAiStream('Gut gemacht heute!', 3)),
  });
  app = await testApp();
  headers = await adminHeaders(app);
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
    url: '/api/ai/tasks/summary',
    headers,
    payload: { providerId: provider.id, model: 'm' },
  });
  await app.inject({ method: 'DELETE', url: '/api/ai/tasks/check', headers });
});
afterEach(async () => {
  await app.close();
  await mock.close();
});

const get = (url: string) => app.inject({ method: 'GET', url: `/api${url}`, headers });

async function seed() {
  const pkg = (
    await app.inject({
      method: 'POST',
      url: '/api/packages',
      headers,
      payload: { name: 'L', language: 'la' },
    })
  ).json().package;
  const vocab = (
    await app.inject({
      method: 'POST',
      url: `/api/packages/${pkg.id}/vocab`,
      headers,
      payload: {
        items: [
          { word: 'amicus', translation: 'Freund' },
          { word: 'bellum', translation: 'Krieg' },
          { word: 'hostis', translation: 'Feind' },
        ],
      },
    })
  ).json().vocab;
  await app.inject({ method: 'POST', url: `/api/vocab/${vocab[0].id}/activate`, headers });
  await app.inject({ method: 'POST', url: `/api/vocab/${vocab[1].id}/activate`, headers });
  return vocab.map((v: { id: number }) => v.id) as number[];
}

const answer = (vocabId: number, body: object) =>
  app.inject({
    method: 'POST',
    url: '/api/learn/answer',
    headers,
    payload: { vocabId, direction: 'foreign_native', ...body },
  });

describe('Startseite', () => {
  it('zählt Fällige, Stufen, Gelernte und Inaktive', async () => {
    const [a, b] = await seed();
    let o = (await get('/overview')).json();
    expect(o).toMatchObject({
      due: 2,
      stages: [2, 0, 0, 0, 0, 0],
      learned: 0,
      inactive: 1,
      nextDue: null,
    });

    await answer(a!, { answer: 'Freund', msToFirstKey: 900, msTotal: 2000 });
    await answer(b!, { answer: 'Frieden', msToFirstKey: 1100, msTotal: 4000 });
    o = (await get('/overview')).json();
    expect(o).toMatchObject({ due: 1, stages: [1, 1, 0, 0, 0, 0] });
    expect(o.todayStats).toMatchObject({
      attempts: 2,
      correct: 1,
      wrong: 1,
      accuracy: 0.5,
      medianMsToFirstKey: 1000,
      medianMsTotal: 3000,
      stageUps: 1,
      difficult: [{ vocabId: b, word: 'bellum', translation: 'Krieg', wrong: 1 }],
    });
  });

  it('zeigt das nächste Fälligkeitsdatum, wenn heute nichts fällig ist', async () => {
    const [a, b] = await seed();
    await answer(a!, { answer: 'Freund' });
    await answer(b!, { answer: 'Krieg' });
    const o = (await get('/overview')).json();
    expect(o.due).toBe(0);
    expect(o.nextDue).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});

describe('Fälligkeiten der nächsten Tage (Erinnerungen)', () => {
  it('zählt Fällige je Tag für 30 Tage, Überfällige inklusive', async () => {
    const [a, b] = await seed();
    await answer(a!, { answer: 'Freund' }); // stage 2, due in 5 days
    const days = (await get('/overview/due-days')).json().days as Array<{
      day: string;
      count: number;
    }>;
    expect(days).toHaveLength(30);
    expect(days[0]!.count).toBe(1); // bellum today
    expect(days[4]!.count).toBe(1);
    expect(days[5]!.count).toBe(2); // amicus again after 5 days
    expect(b).toBeDefined();
  });
});
