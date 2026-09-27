import { afterEach, describe, expect, it } from 'vitest';
import { createProvider, ProviderError } from '../src/ai';
import { SecretBox } from '../src/ai/crypto';
import { json, mockServer, openAiStream, stream } from './mock-ai';

const servers: Array<{ close: () => Promise<void> }> = [];
afterEach(async () => {
  for (const s of servers.splice(0)) await s.close();
});
async function server(handlers: Parameters<typeof mockServer>[0]) {
  const s = await mockServer(handlers);
  servers.push(s);
  return s;
}

describe('Verschlüsselung der API-Schlüssel', () => {
  it('verschlüsselt mit AES-256-GCM und entschlüsselt wieder', () => {
    const box = new SecretBox('geheim');
    const enc = box.encrypt('sk_test_1234');
    expect(enc).toMatch(/^v1:/);
    expect(enc).not.toContain('sk_test');
    expect(box.decrypt(enc)).toBe('sk_test_1234');
    expect(box.encrypt('sk_test_1234')).not.toBe(enc); // random IV
  });

  it('erkennt Manipulation und falschen Hauptschlüssel', () => {
    const enc = new SecretBox('geheim').encrypt('sk_test_1234');
    expect(() => new SecretBox('anders').decrypt(enc)).toThrow();
    const raw = Buffer.from(enc.slice(3), 'base64');
    raw[raw.length - 1]! ^= 1;
    expect(() => new SecretBox('geheim').decrypt(`v1:${raw.toString('base64')}`)).toThrow();
  });
});

describe('OpenAI-kompatibel (Pollinations, OpenAI, OpenRouter, Custom)', () => {
  it('streamt Text, misst Latenz und schickt Bilder als data-URL', async () => {
    const s = await server({
      'POST /v1/chat/completions': (_req, res) =>
        stream(res, 'text/event-stream', openAiStream('richtig', 1)),
    });
    const p = createProvider('openai', { baseUrl: `${s.url}/v1`, apiKey: 'sk_abc' });
    const result = await p.chat({
      model: 'openai/gpt-oss-20b',
      messages: [
        { role: 'system', content: 'sys' },
        { role: 'user', content: 'Bild', images: [{ mediaType: 'image/jpeg', base64: 'AAAA' }] },
      ],
      maxTokens: 5,
      reasoningEffort: 'low',
    });
    expect(result.text).toBe('richtig');
    expect(result.metrics.ttftMs).toBeGreaterThanOrEqual(0);
    expect(result.metrics.totalMs).toBeGreaterThanOrEqual(result.metrics.ttftMs!);
    const req = s.requests[0]!;
    expect(req.headers.authorization).toBe('Bearer sk_abc');
    expect(req.body).toMatchObject({
      model: 'openai/gpt-oss-20b',
      stream: true,
      max_tokens: 5,
      reasoning_effort: 'low',
    });
    expect(req.body.messages[1].content[1]).toEqual({
      type: 'image_url',
      image_url: { url: 'data:image/jpeg;base64,AAAA' },
    });
  });

  it('sendet ohne Schlüssel keinen Authorization-Header', async () => {
    const s = await server({ 'GET /v1/models': (_req, res) => json(res, 200, { data: [] }) });
    await createProvider('openai', { baseUrl: `${s.url}/v1` }).listModels();
    expect(s.requests[0]!.headers.authorization).toBeUndefined();
  });

  it('liest Bildfähigkeit aus input_modalities (Pollinations) und architecture (OpenRouter)', async () => {
    const s = await server({
      'GET /v1/models': (_req, res) =>
        json(res, 200, {
          data: [
            {
              id: 'openai/gpt-oss-20b',
              category: 'text',
              input_modalities: ['text'],
              reasoning: true,
            },
            {
              id: 'google/gemini-3.7-flash',
              category: 'text',
              input_modalities: ['text', 'image'],
            },
            { id: 'flux', category: 'image' },
            { id: 'or/model', architecture: { input_modalities: ['text', 'image'] } },
            { id: 'plain' },
          ],
        }),
    });
    const models = await createProvider('openai', { baseUrl: `${s.url}/v1` }).listModels();
    expect(models).toEqual([
      { id: 'openai/gpt-oss-20b', name: undefined, vision: false, reasoning: true },
      { id: 'google/gemini-3.7-flash', name: undefined, vision: true, reasoning: null },
      { id: 'or/model', name: undefined, vision: true, reasoning: null },
      { id: 'plain', name: undefined, vision: null, reasoning: null },
    ]);
  });

  it('übersetzt 401 und 402 in verständliche Fehler', async () => {
    const s = await server({
      'GET /v1/models': (_req, res) =>
        json(res, 401, { error: { code: 'UNAUTHORIZED', message: 'Missing key' } }),
      'POST /v1/chat/completions': (_req, res) =>
        json(res, 402, { error: { message: 'Insufficient pollen' } }),
    });
    const p = createProvider('openai', { baseUrl: `${s.url}/v1` });
    await expect(p.listModels()).rejects.toMatchObject({
      code: 'auth',
      status: 401,
      message: 'Missing key',
    });
    await expect(p.chat({ model: 'x', messages: [] })).rejects.toMatchObject({
      code: 'payment',
      status: 402,
    });
  });

  it('bricht bei Zeitüberschreitung ab', async () => {
    const s = await server({
      'POST /v1/chat/completions': async (_req, res) => {
        await new Promise((r) => setTimeout(r, 300));
        json(res, 200, {});
      },
    });
    const p = createProvider('openai', { baseUrl: `${s.url}/v1` });
    await expect(
      p.chat({ model: 'x', messages: [], signal: AbortSignal.timeout(50) }),
    ).rejects.toMatchObject({
      code: 'timeout',
    });
  });

  it('meldet nicht erreichbare Server als Netzwerkfehler', async () => {
    const p = createProvider('openai', { baseUrl: 'http://127.0.0.1:1/v1' });
    const err = await p.listModels().catch((e) => e);
    expect(err).toBeInstanceOf(ProviderError);
    expect(err.code).toBe('network');
  });
});

describe('Anthropic Messages API', () => {
  it('streamt Text, trennt System-Prompt und schickt Bilder als base64-Block', async () => {
    const events = [
      { type: 'message_start', message: {} },
      { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } },
      { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'tipp' } },
      { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text: 'fehler' } },
      { type: 'message_delta', delta: { stop_reason: 'end_turn' }, usage: { output_tokens: 3 } },
      { type: 'message_stop' },
    ];
    const s = await server({
      'POST /v1/messages': (_req, res) =>
        stream(
          res,
          'text/event-stream',
          events.map((e) => `event: ${e.type}\ndata: ${JSON.stringify(e)}\n\n`),
        ),
    });
    const p = createProvider('anthropic', { baseUrl: `${s.url}/v1`, apiKey: 'sk-ant' });
    const result = await p.chat({
      model: 'claude-x',
      messages: [
        { role: 'system', content: 'Du prüfst.' },
        { role: 'user', content: 'Foto', images: [{ mediaType: 'image/png', base64: 'BBBB' }] },
      ],
    });
    expect(result.text).toBe('tippfehler');
    expect(result.metrics.tokensPerSec).not.toBeNull();
    const req = s.requests[0]!;
    expect(req.headers['x-api-key']).toBe('sk-ant');
    expect(req.headers['anthropic-version']).toBe('2023-06-01');
    expect(req.body.system).toBe('Du prüfst.');
    expect(req.body.messages).toHaveLength(1);
    expect(req.body.messages[0].content[0]).toEqual({
      type: 'image',
      source: { type: 'base64', media_type: 'image/png', data: 'BBBB' },
    });
  });

  it('listet Modelle seitenweise mit Bildfähigkeit', async () => {
    const s = await server({
      'GET /v1/models': (req, res) =>
        req.url.includes('after_id')
          ? json(res, 200, { data: [{ id: 'm2', display_name: 'M2' }], has_more: false })
          : json(res, 200, {
              data: [
                {
                  id: 'm1',
                  display_name: 'M1',
                  capabilities: { image_input: { supported: true } },
                },
              ],
              has_more: true,
              last_id: 'm1',
            }),
    });
    const models = await createProvider('anthropic', {
      baseUrl: `${s.url}/v1`,
      apiKey: 'k',
    }).listModels();
    expect(models).toEqual([
      { id: 'm1', name: 'M1', vision: true, reasoning: null },
      { id: 'm2', name: 'M2', vision: null, reasoning: null },
    ]);
  });
});

describe('Ollama', () => {
  it('streamt NDJSON, nutzt keep_alive und schickt Bilder als base64-Liste', async () => {
    const s = await server({
      'POST /api/chat': (_req, res) =>
        stream(res, 'application/x-ndjson', [
          JSON.stringify({ message: { content: 'fal' }, done: false }) + '\n',
          JSON.stringify({ message: { content: 'sch' }, done: false }) + '\n',
          JSON.stringify({ message: { content: '' }, done: true, eval_count: 2 }) + '\n',
        ]),
    });
    const p = createProvider('ollama', { baseUrl: s.url });
    const result = await p.chat({
      model: 'llama3.2',
      messages: [
        { role: 'user', content: 'x', images: [{ mediaType: 'image/jpeg', base64: 'CCCC' }] },
      ],
      keepAlive: '30m',
      maxTokens: 4,
    });
    expect(result.text).toBe('falsch');
    expect(s.requests[0]!.body).toMatchObject({
      model: 'llama3.2',
      keep_alive: '30m',
      options: { num_predict: 4 },
      messages: [{ role: 'user', content: 'x', images: ['CCCC'] }],
    });
    expect(s.requests[0]!.headers.authorization).toBeUndefined();
  });

  it('Ollama online schickt den Schlüssel als Bearer', async () => {
    const s = await server({ 'GET /api/tags': (_req, res) => json(res, 200, { models: [] }) });
    await createProvider('ollama', { baseUrl: s.url, apiKey: 'ollama-key' }).listModels();
    expect(s.requests[0]!.headers.authorization).toBe('Bearer ollama-key');
  });

  it('erkennt Bildmodelle über /api/show und blendet reine Embedding-Modelle aus', async () => {
    const caps: Record<string, string[]> = {
      'llava:latest': ['completion', 'vision'],
      'llama3.2:latest': ['completion'],
      'nomic-embed-text:latest': ['embedding'],
    };
    const s = await server({
      'GET /api/tags': (_req, res) =>
        json(res, 200, { models: Object.keys(caps).map((name) => ({ name })) }),
      'POST /api/show': (req, res) => json(res, 200, { capabilities: caps[req.body.model] }),
    });
    const models = await createProvider('ollama', { baseUrl: s.url }).listModels();
    expect(models).toEqual([
      { id: 'llava:latest', vision: true, reasoning: false },
      { id: 'llama3.2:latest', vision: false, reasoning: false },
    ]);
  });

  it('wärmt das Modell mit leerer Nachrichtenliste vor', async () => {
    const s = await server({ 'POST /api/chat': (_req, res) => json(res, 200, { done: true }) });
    await createProvider('ollama', { baseUrl: s.url }).warmUp('llama3.2');
    expect(s.requests[0]!.body).toEqual({ model: 'llama3.2', messages: [], keep_alive: '30m' });
  });
});
