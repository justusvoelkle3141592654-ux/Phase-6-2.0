import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { ORIGIN, register, sessionCookie, testApp } from './helpers';
import { json, mockServer, openAiStream, stream } from './mock-ai';

let app: FastifyInstance;
let headers: Record<string, string>;
let mock: Awaited<ReturnType<typeof mockServer>>;

beforeEach(async () => {
  app = await testApp();
  headers = { origin: ORIGIN, cookie: `gero_session=${sessionCookie(await register(app))}` };
  mock = await mockServer({
    'GET /v1/models': (req, res) =>
      req.headers.authorization === 'Bearer sk_bad'
        ? json(res, 401, { error: { message: 'invalid key' } })
        : json(res, 200, {
            data: [
              { id: 'text-model', input_modalities: ['text'] },
              { id: 'vision-model', input_modalities: ['text', 'image'] },
            ],
          }),
    'POST /v1/chat/completions': (_req, res) =>
      stream(res, 'text/event-stream', openAiStream('OK', 1)),
  });
});
afterEach(async () => {
  await app.close();
  await mock.close();
});

function req(method: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE', url: string, payload?: object) {
  return app.inject({ method, url: `/api${url}`, headers, payload });
}

async function customProvider(apiKey?: string) {
  const res = await req('POST', '/ai/providers', {
    kind: 'custom',
    baseUrl: `${mock.url}/v1/`,
    apiKey,
  });
  return res.json().provider;
}

describe('KI-Einrichtung', () => {
  it('neue Accounts haben Pollinations mit openai/gpt-oss-20b ohne Schlüssel', async () => {
    const config = (await req('GET', '/ai/config')).json();
    expect(config.providers).toEqual([
      expect.objectContaining({
        kind: 'pollinations',
        baseUrl: 'https://gen.pollinations.ai/v1',
        protocol: 'openai',
        hasKey: false,
        keyHint: null,
      }),
    ]);
    const id = config.providers[0].id;
    expect(config.tasks).toEqual({
      check: { providerId: id, model: 'openai/gpt-oss-20b', reasoning: null },
      summary: { providerId: id, model: 'openai/gpt-oss-20b', reasoning: null },
      vision: null,
    });
    // Idempotent.
    expect((await req('GET', '/ai/config')).json().providers).toHaveLength(1);
  });

  it('gibt API-Schlüssel nie heraus, nur einen Hinweis', async () => {
    const res = await req('POST', '/ai/providers', {
      kind: 'anthropic',
      apiKey: 'sk-ant-secret-9876',
    });
    expect(res.statusCode).toBe(201);
    const provider = res.json().provider;
    expect(provider).toMatchObject({
      kind: 'anthropic',
      name: 'Anthropic (Claude)',
      baseUrl: 'https://api.anthropic.com/v1',
      protocol: 'anthropic',
      hasKey: true,
      keyHint: '…9876',
    });
    const config = await req('GET', '/ai/config');
    expect(config.body).not.toContain('secret');

    const cleared = await req('PATCH', `/ai/providers/${provider.id}`, { apiKey: null });
    expect(cleared.json().provider).toMatchObject({ hasKey: false, keyHint: null });
  });

  it('Custom braucht eine URL und nimmt OpenAI- oder Ollama-Format', async () => {
    expect((await req('POST', '/ai/providers', { kind: 'custom' })).statusCode).toBe(400);
    const res = await req('POST', '/ai/providers', {
      kind: 'custom',
      baseUrl: 'http://192.168.1.50:11434/',
      protocol: 'ollama',
    });
    expect(res.json().provider).toMatchObject({
      baseUrl: 'http://192.168.1.50:11434',
      protocol: 'ollama',
    });
    expect(
      (await req('POST', '/ai/providers', { kind: 'custom', baseUrl: 'ftp://x' })).statusCode,
    ).toBe(400);
  });

  it('listet Modelle live, auch nur bildfähige', async () => {
    const p = await customProvider();
    const all = (await req('GET', `/ai/providers/${p.id}/models`)).json().models;
    expect(all.map((m: { id: string }) => m.id)).toEqual(['text-model', 'vision-model']);
    const vision = (await req('GET', `/ai/providers/${p.id}/models?vision=1`)).json().models;
    expect(vision.map((m: { id: string }) => m.id)).toEqual(['vision-model']);
  });

  it('„Verbindung testen“ misst die Latenz und meldet Schlüsselfehler', async () => {
    const p = await customProvider();
    const ok = (await req('POST', `/ai/providers/${p.id}/test`, { model: 'text-model' })).json();
    expect(ok).toMatchObject({ ok: true, models: { count: 2 }, chat: { reply: 'OK' } });
    expect(ok.chat.totalMs).toBeGreaterThanOrEqual(0);

    const bad = await customProvider('sk_bad');
    const fail = (await req('POST', `/ai/providers/${bad.id}/test`, {})).json();
    expect(fail).toMatchObject({ ok: false, error: { code: 'auth', status: 401 } });

    const stats = (await req('GET', '/ai/latency')).json().stats;
    expect(stats.find((s: { task: string }) => s.task === 'test')).toMatchObject({
      count: 1,
      failures: 0,
    });
  });

  it('setzt Modell je Aufgabe; Löschen des Anbieters entfernt die Zuordnung', async () => {
    const p = await customProvider();
    const res = await req('PUT', '/ai/tasks/vision', { providerId: p.id, model: 'vision-model' });
    expect(res.json().tasks.vision).toEqual({
      providerId: p.id,
      model: 'vision-model',
      reasoning: null,
    });
    expect(
      (await req('PUT', '/ai/tasks/unsinn', { providerId: p.id, model: 'x' })).statusCode,
    ).toBe(404);
    await req('DELETE', `/ai/providers/${p.id}`);
    expect((await req('GET', '/ai/config')).json().tasks.vision).toBeNull();
  });

  it('trennt die KI-Einstellungen der Accounts', async () => {
    const p = await customProvider();
    headers = {
      origin: ORIGIN,
      cookie: `gero_session=${sessionCookie(await register(app, { email: 'bob@example.org' }))}`,
    };
    expect((await req('GET', `/ai/providers/${p.id}/models`)).statusCode).toBe(404);
    expect((await req('PUT', '/ai/tasks/check', { providerId: p.id, model: 'x' })).statusCode).toBe(
      404,
    );
  });
});
