import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { extractJson, parseRecognition } from '../src/uploads/recognize';
import { adminHeaders, ORIGIN, register, sessionCookie, testApp } from './helpers';
import { json, mockServer, openAiStream, stream } from './mock-ai';

const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4, 0xff, 0xd9]);

async function multipart(files: Array<{ type: string; data: Buffer }>) {
  const form = new FormData();
  files.forEach((f, i) =>
    form.append('photos', new Blob([f.data], { type: f.type }), `photo${i}.jpg`),
  );
  const req = new Request('http://x', { method: 'POST', body: form });
  return {
    body: Buffer.from(await req.arrayBuffer()),
    contentType: req.headers.get('content-type')!,
  };
}

describe('Antwort der Bilderkennung lesen', () => {
  it('findet das JSON auch in Code-Blöcken und prüft es mit zod', () => {
    const text =
      'Hier:\n```json\n{"entries":[{"word":"amicus","extra":"amici m.","translations":["Freund"],"language":"la"}]}\n```';
    expect(parseRecognition(text)).toEqual([
      { word: 'amicus', extra: 'amici m.', translations: ['Freund'], language: 'la' },
    ]);
    expect(parseRecognition('{"entries":[{"word":"dog","translations":"Hund"}]}')).toEqual([
      { word: 'dog', extra: '', translations: ['Hund'] },
    ]);
    expect(parseRecognition('{"entries":[{"word":"","translations":[]}]}')).toBeNull();
    expect(parseRecognition('keine Ahnung')).toBeNull();
    expect(extractJson('{kaputt')).toBeNull();
  });
});

describe('Hochladen und Erkennen', () => {
  let app: FastifyInstance;
  let headers: Record<string, string>;
  let mock: Awaited<ReturnType<typeof mockServer>>;
  let replies: string[];

  beforeEach(async () => {
    replies = [];
    mock = await mockServer({
      'GET /v1/models': (_req, res) =>
        json(res, 200, { data: [{ id: 'reader', input_modalities: ['text', 'image'] }] }),
      'POST /v1/chat/completions': (_req, res) =>
        stream(res, 'text/event-stream', openAiStream(replies.shift() ?? '', 1)),
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
      url: '/api/ai/tasks/vision',
      headers,
      payload: { providerId: provider.id, model: 'reader' },
    });
  });
  afterEach(async () => {
    await app.close();
    await mock.close();
  });

  async function upload(files = [{ type: 'image/jpeg', data: JPEG }]) {
    const { body, contentType } = await multipart(files);
    return app.inject({
      method: 'POST',
      url: '/api/uploads',
      headers: { ...headers, 'content-type': contentType },
      payload: body,
    });
  }

  async function waitForJob(id: number) {
    for (let i = 0; i < 100; i++) {
      const job = (await app.inject({ method: 'GET', url: `/api/uploads/${id}`, headers })).json()
        .job;
      if (job.pages.every((p: { status: string }) => p.status === 'done' || p.status === 'failed'))
        return job;
      await new Promise((r) => setTimeout(r, 20));
    }
    throw new Error('Erkennung nicht fertig');
  }

  const ENTRIES =
    '{"entries":[{"word":"amicus","extra":"amici m.","translations":["Freund","Kamerad"],"language":"la"}]}';

  it('erkennt Vokabeln auf mehreren Fotos und schickt das Bild ans Bildmodell', async () => {
    replies = [ENTRIES, '{"entries":[{"word":"bellum","translations":["Krieg"]}]}'];
    const res = await upload([
      { type: 'image/jpeg', data: JPEG },
      { type: 'image/png', data: Buffer.from([0x89, 0x50, 0x4e, 0x47]) },
    ]);
    expect(res.statusCode).toBe(201);
    const job = await waitForJob(res.json().job.id);
    expect(job.pages.map((p: { status: string }) => p.status)).toEqual(['done', 'done']);
    expect(job.pages[0].entries).toEqual([
      { word: 'amicus', extra: 'amici m.', translations: ['Freund', 'Kamerad'], language: 'la' },
    ]);
    const chat = mock.requests.find((r) => r.url === '/v1/chat/completions')!.body;
    expect(chat.model).toBe('reader');
    expect(chat.messages[1].content[1].image_url.url).toBe(
      `data:image/jpeg;base64,${JPEG.toString('base64')}`,
    );
  });

  it('wiederholt einmal bei falschem Format', async () => {
    replies = ['Das sind Vokabeln über Freunde.', ENTRIES];
    const job = await waitForJob((await upload()).json().job.id);
    expect(job.pages[0].status).toBe('done');
    const chats = mock.requests.filter((r) => r.url === '/v1/chat/completions');
    expect(chats).toHaveLength(2);
    expect(chats[1]!.body.messages.at(-1).content).toContain('kein gültiges JSON');
  });

  it('meldet zweimal falsches Format als Fehler; erneut versuchen geht', async () => {
    replies = ['nein', 'immer noch nein'];
    const job = await waitForJob((await upload()).json().job.id);
    expect(job.pages[0]).toMatchObject({ status: 'failed', error: 'invalid_format' });
    replies = [ENTRIES];
    await app.inject({
      method: 'POST',
      url: `/api/uploads/pages/${job.pages[0].id}/retry`,
      headers,
    });
    expect((await waitForJob(job.id)).pages[0].status).toBe('done');
  });

  it('ohne Bildmodell: verständlicher Fehler', async () => {
    await app.inject({ method: 'DELETE', url: '/api/ai/tasks/vision', headers });
    const job = await waitForJob((await upload()).json().job.id);
    expect(job.pages[0]).toMatchObject({ status: 'failed', error: 'no_vision_model' });
  });

  it('lehnt andere Dateitypen ab', async () => {
    const res = await upload([{ type: 'application/pdf', data: Buffer.from('%PDF') }]);
    expect(res.statusCode).toBe(415);
  });

  async function createPackage(name = 'Lektion 4') {
    return (
      await app.inject({
        method: 'POST',
        url: '/api/packages',
        headers,
        payload: { name, language: 'la' },
      })
    ).json().package;
  }

  function userHeaders(email = 'bob@example.org') {
    return register(app, { email }).then((res) => ({
      origin: ORIGIN,
      cookie: `wordflow_session=${sessionCookie(res)}`,
    }));
  }

  it('speichert ins Paket; die Fotos sehen alle Accounts', async () => {
    replies = [ENTRIES];
    const pkg = await createPackage();
    const job = await waitForJob((await upload()).json().job.id);
    const saved = await app.inject({
      method: 'POST',
      url: `/api/uploads/${job.id}/save`,
      headers,
      payload: {
        packageId: pkg.id,
        items: [
          { word: 'amicus', extra: 'amici m.', translation: 'Freund; Kamerad' },
          { word: 'hostis', translation: 'Feind' },
        ],
      },
    });
    expect(saved.json()).toEqual({ packageId: pkg.id, count: 2 });
    const detail = (
      await app.inject({ method: 'GET', url: `/api/packages/${pkg.id}`, headers })
    ).json();
    expect(detail.vocab.map((v: { word: string; active: boolean }) => [v.word, v.active])).toEqual([
      ['amicus', false],
      ['hostis', false],
    ]);

    const bob = await userHeaders();
    const photos = (
      await app.inject({ method: 'GET', url: `/api/packages/${pkg.id}/photos`, headers: bob })
    ).json().photos;
    expect(photos).toHaveLength(1);
    const photo = await app.inject({ method: 'GET', url: photos[0].photoUrl, headers: bob });
    expect(photo.statusCode).toBe(200);
    expect(photo.headers['content-type']).toBe('image/jpeg');
    expect(photo.rawPayload.equals(JPEG)).toBe(true);

    // Saved uploads no longer appear as open.
    expect(
      (await app.inject({ method: 'GET', url: '/api/uploads', headers })).json().jobs,
    ).toHaveLength(0);
  });

  it('speichert nicht in unbekannte Pakete', async () => {
    replies = [ENTRIES];
    const job = await waitForJob((await upload()).json().job.id);
    const res = await app.inject({
      method: 'POST',
      url: `/api/uploads/${job.id}/save`,
      headers,
      payload: { packageId: 999, items: [{ word: 'amicus', translation: 'Freund' }] },
    });
    expect(res.statusCode).toBe(404);
  });

  it('Hochladen hängt Fotos ohne Erkennung ans Paket; ✕ löscht ein Foto', async () => {
    const pkg = await createPackage();
    const { body, contentType } = await multipart([
      { type: 'image/jpeg', data: JPEG },
      { type: 'image/png', data: JPEG },
    ]);
    const res = await app.inject({
      method: 'POST',
      url: `/api/packages/${pkg.id}/photos`,
      headers: { ...headers, 'content-type': contentType },
      payload: body,
    });
    expect(res.statusCode).toBe(201);
    expect(res.json().job).toMatchObject({ packageId: pkg.id });
    expect(res.json().job.pages.map((p: { status: string }) => p.status)).toEqual(['done', 'done']);
    expect(replies).toEqual([]);

    const list = async () =>
      (await app.inject({ method: 'GET', url: `/api/packages/${pkg.id}/photos`, headers })).json()
        .photos as Array<{ id: number; photoUrl: string }>;
    const [first, second] = await list();

    const bob = await userHeaders();
    const forbidden = await app.inject({
      method: 'DELETE',
      url: `/api/uploads/pages/${first!.id}`,
      headers: bob,
    });
    expect(forbidden.statusCode).toBe(403);

    const del = await app.inject({
      method: 'DELETE',
      url: `/api/uploads/pages/${first!.id}`,
      headers,
    });
    expect(del.statusCode).toBe(204);
    expect((await list()).map((p) => p.id)).toEqual([second!.id]);
    expect((await app.inject({ method: 'GET', url: first!.photoUrl, headers })).statusCode).toBe(
      404,
    );
  });

  it('löscht offene Uploads samt Fotos', async () => {
    replies = [ENTRIES];
    const job = await waitForJob((await upload()).json().job.id);
    const photoUrl = job.pages[0].photoUrl;
    expect(
      (await app.inject({ method: 'DELETE', url: `/api/uploads/${job.id}`, headers })).statusCode,
    ).toBe(204);
    expect((await app.inject({ method: 'GET', url: photoUrl, headers })).statusCode).toBe(404);
  });

  it('normale Accounts können nicht hochladen und sehen keine offenen Uploads', async () => {
    replies = [ENTRIES];
    const job = await waitForJob((await upload()).json().job.id);
    const bob = await userHeaders();
    const { body, contentType } = await multipart([{ type: 'image/jpeg', data: JPEG }]);
    expect(
      (
        await app.inject({
          method: 'POST',
          url: '/api/uploads',
          headers: { ...bob, 'content-type': contentType },
          payload: body,
        })
      ).statusCode,
    ).toBe(403);
    expect(
      (await app.inject({ method: 'GET', url: `/api/uploads/${job.id}`, headers: bob })).statusCode,
    ).toBe(403);
    expect(
      (await app.inject({ method: 'GET', url: job.pages[0].photoUrl, headers: bob })).statusCode,
    ).toBe(404);
    expect(
      (
        await app.inject({
          method: 'POST',
          url: `/api/uploads/${job.id}/save`,
          headers: bob,
          payload: { packageId: 1, items: [{ word: 'a', translation: 'b' }] },
        })
      ).statusCode,
    ).toBe(403);
  });
});
