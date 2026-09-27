import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { addDays, todayIn } from '@gero/shared';
import { ORIGIN, register, sessionCookie, testApp } from './helpers';

let app: FastifyInstance;
let headers: Record<string, string>;
const today = () => todayIn('Europe/Berlin');

beforeEach(async () => {
  app = await testApp();
  headers = {
    origin: ORIGIN,
    cookie: `gero_session=${sessionCookie(await register(app, { timezone: 'Europe/Berlin' }))}`,
  };
});
afterEach(async () => {
  await app.close();
});

function req(method: 'GET' | 'POST' | 'PATCH', url: string, payload?: object) {
  return app.inject({ method, url: `/api${url}`, headers, payload });
}

async function seed(
  items = [{ word: 'amicus', extra: 'amici m.', translation: 'Freund; Kamerad' }],
  activate = true,
) {
  const pkg = (await req('POST', '/packages', { name: 'L1', language: 'la' })).json().package;
  const vocab = (await req('POST', `/packages/${pkg.id}/vocab`, { items })).json().vocab;
  if (activate) await req('POST', `/packages/${pkg.id}/activate`);
  return { pkg, vocab };
}

function answer(vocabId: number, body: object) {
  return req('POST', '/learn/answer', { vocabId, direction: 'foreign_native', ...body });
}

describe('Fällige Karten', () => {
  it('liefert nur aktive, fällige, nicht gelernte Vokabeln', async () => {
    const { pkg } = await seed([
      { word: 'a', translation: 'A' },
      { word: 'b', translation: 'B' },
    ]);
    await seed([{ word: 'inaktiv', translation: 'x' }], false);
    const res = await req('GET', '/learn/cards');
    expect(res.json().cards).toHaveLength(2);
    expect(res.json().cards[0]).toMatchObject({
      packageId: pkg.id,
      packageName: 'L1',
      language: 'la',
      stage: 1,
    });

    const card = res.json().cards[0];
    await answer(card.vocabId, { answer: card.translation });
    expect((await req('GET', '/learn/cards')).json().cards).toHaveLength(1);
    expect((await req('GET', `/learn/cards?packageId=${pkg.id + 99}`)).json().cards).toHaveLength(
      0,
    );
  });
});

describe('Antworten', () => {
  it('exakt richtig: Stufe 2, fällig in 5 Tagen, Abfrage gespeichert', async () => {
    const { pkg, vocab } = await seed();
    const res = await answer(vocab[0].id, {
      answer: ' kamerad ',
      msToFirstKey: 800,
      msTotal: 2100,
    });
    expect(res.json()).toMatchObject({
      correct: true,
      decidedBy: 'exact',
      stageBefore: 1,
      stageAfter: 2,
      learned: false,
      canOverride: false,
    });
    const detail = (await req('GET', `/packages/${pkg.id}`)).json();
    expect(detail.vocab[0]).toMatchObject({ stage: 2, dueDate: addDays(today(), 5) });
  });

  it('Tippfehler: richtig mit Hinweis auf die Schreibweise', async () => {
    const { vocab } = await seed();
    const res = await answer(vocab[0].id, { answer: 'Fruend' });
    expect(res.json()).toMatchObject({ correct: true, decidedBy: 'typo', typoOf: 'freund' });
  });

  it('leer: falsch ohne „Ich hatte recht“', async () => {
    const { vocab } = await seed();
    const res = await answer(vocab[0].id, { answer: '   ' });
    expect(res.json()).toMatchObject({ correct: false, decidedBy: 'local', canOverride: false });
  });

  it('unklar ohne KI: lokal falsch, mit „Ich hatte recht“', async () => {
    const { vocab } = await seed();
    const res = await answer(vocab[0].id, { answer: 'Kumpel' });
    expect(res.json()).toMatchObject({
      correct: false,
      decidedBy: 'local',
      canOverride: true,
      stageAfter: 1,
    });
  });

  it('„Ich hatte recht“ korrigiert Stufe und merkt sich die Antwort', async () => {
    const { pkg, vocab } = await seed();
    const wrong = (await answer(vocab[0].id, { answer: 'Kumpel' })).json();
    const fixed = await req('POST', `/learn/attempts/${wrong.attemptId}/override`);
    expect(fixed.json()).toMatchObject({ correct: true, decidedBy: 'correction', stageAfter: 2 });
    expect((await req('GET', `/packages/${pkg.id}`)).json().vocab[0].stage).toBe(2);

    // Only once.
    expect((await req('POST', `/learn/attempts/${wrong.attemptId}/override`)).statusCode).toBe(409);

    // Next time the same answer counts as correct.
    const again = await answer(vocab[0].id, { answer: 'kumpel!' });
    expect(again.json()).toMatchObject({ correct: true, decidedBy: 'correction', stageAfter: 3 });
  });

  it('„Ich hatte recht“ geht nicht bei exakten oder selbst bewerteten Antworten', async () => {
    const { vocab } = await seed();
    const self = (await answer(vocab[0].id, { answer: null, selfGrade: false })).json();
    expect(self).toMatchObject({ correct: false, decidedBy: 'self' });
    expect((await req('POST', `/learn/attempts/${self.attemptId}/override`)).statusCode).toBe(409);
  });

  it('Selbstbewertung: gewusst = eine Stufe höher', async () => {
    const { vocab } = await seed();
    const res = await answer(vocab[0].id, { answer: null, selfGrade: true });
    expect(res.json()).toMatchObject({ correct: true, decidedBy: 'self', stageAfter: 2 });
  });

  it('Richtung Deutsch → Fremdsprache prüft gegen das Wort (ohne Zusatz)', async () => {
    const { vocab } = await seed();
    const ok = await req('POST', '/learn/answer', {
      vocabId: vocab[0].id,
      direction: 'native_foreign',
      answer: 'Amicus',
    });
    expect(ok.json()).toMatchObject({ correct: true, decidedBy: 'exact' });
  });

  it('falsch setzt nach Einstellung zurück', async () => {
    const { vocab } = await seed();
    await answer(vocab[0].id, { answer: 'Freund' }); // stage 2
    await answer(vocab[0].id, { answer: 'Freund' }); // stage 3
    await req('PATCH', '/settings', { wrongMode: 'back' });
    const res = await answer(vocab[0].id, { answer: null, selfGrade: false });
    expect(res.json()).toMatchObject({ stageBefore: 3, stageAfter: 2 });
    await req('PATCH', '/settings', { wrongMode: 'reset' });
    const reset = await answer(vocab[0].id, { answer: null, selfGrade: false });
    expect(reset.json()).toMatchObject({ stageBefore: 2, stageAfter: 1 });
  });

  it('richtig auf Stufe 6: gelernt, danach nicht mehr abfragbar', async () => {
    const { vocab } = await seed();
    for (let i = 0; i < 5; i++) await answer(vocab[0].id, { answer: null, selfGrade: true });
    const last = await answer(vocab[0].id, { answer: null, selfGrade: true });
    expect(last.json()).toMatchObject({ stageBefore: 6, stageAfter: 6, learned: true });
    expect((await answer(vocab[0].id, { answer: null, selfGrade: true })).statusCode).toBe(404);
  });

  it('verlangt Antwort oder Selbstbewertung', async () => {
    const { vocab } = await seed();
    expect((await answer(vocab[0].id, { answer: null })).statusCode).toBe(400);
  });

  it('lässt keine fremden Vokabeln zu', async () => {
    const { vocab } = await seed();
    headers = {
      origin: ORIGIN,
      cookie: `gero_session=${sessionCookie(await register(app, { email: 'bob@example.org' }))}`,
    };
    expect((await answer(vocab[0].id, { answer: 'Freund' })).statusCode).toBe(404);
  });
});
