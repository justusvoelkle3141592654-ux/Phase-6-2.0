import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import type { AnswerResult, LearnCard, PackageDto } from '@gero/shared';
import { mockApi, renderApp, USER } from './utils';

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const PKG: PackageDto = {
  id: 1,
  name: 'Lektion 3',
  language: 'la',
  direction: 'foreign_native',
  createdAt: '',
  counts: { total: 2, inactive: 0, active: 2, due: 2, learned: 0 },
};
const card = (id: number, word: string, translation: string, extra = ''): LearnCard => ({
  vocabId: id,
  packageId: 1,
  packageName: 'Lektion 3',
  language: 'la',
  packageDirection: 'foreign_native',
  word,
  extra,
  translation,
  stage: 1,
});
const CARDS = [card(1, 'amicus', 'Freund', 'amici m.'), card(2, 'bellum', 'Krieg')];

function result(correct: boolean, extra: Partial<AnswerResult> = {}): AnswerResult {
  return {
    attemptId: 10,
    correct,
    decidedBy: 'self',
    stageBefore: 1,
    stageAfter: correct ? 2 : 1,
    learned: false,
    canOverride: false,
    ...extra,
  };
}

function api(answer: (body: Record<string, unknown>) => AnswerResult) {
  return mockApi({
    'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
    'GET /packages': () => ({ status: 200, body: { packages: [PKG] } }),
    'GET /learn/cards': () => ({ status: 200, body: { cards: CARDS } }),
    'GET /learn/cards?packageId=1': () => ({ status: 200, body: { cards: CARDS } }),
    'POST /learn/answer': (body) => ({
      status: 200,
      body: answer(body as Record<string, unknown>),
    }),
    'POST /learn/attempts/10/override': () => ({
      status: 200,
      body: result(true, { decidedBy: 'correction' }),
    }),
  });
}

async function start() {
  renderApp('/learn');
  fireEvent.click(await screen.findByRole('button', { name: 'Alle fälligen lernen' }));
  await screen.findByText('Karte 1 von 2');
}

describe('Lernen', () => {
  it('zeigt die fälligen Vokabeln je Paket', async () => {
    api(() => result(true));
    renderApp('/learn');
    expect(await screen.findByText('2 Vokabeln fällig')).toBeTruthy();
    expect(screen.getByText('Latein · 2 fällig')).toBeTruthy();
  });

  it('Weg B: umdrehen und selbst bewerten, falsche kommen am Ende nochmal', async () => {
    const calls = api((body) => result(body.selfGrade as boolean));
    await start();
    // Card order is random on the server; this mock keeps it.
    fireEvent.click(screen.getByRole('button', { name: 'Umdrehen: amicus' }));
    expect(screen.getByText('amici m.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Nicht gewusst/ }));

    expect(await screen.findByText('Karte 2 von 3')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Umdrehen: bellum' }));
    fireEvent.keyDown(window, { key: 'ArrowRight' });

    expect(await screen.findByText('Karte 3 von 3')).toBeTruthy();
    expect(screen.getByText(/Wiederholung/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Umdrehen: amicus' }));
    fireEvent.click(screen.getByRole('button', { name: /Gewusst/ }));

    expect(await screen.findByRole('heading', { name: 'Geschafft' })).toBeTruthy();
    expect(screen.getByText('1 richtig, 1 falsch.')).toBeTruthy();
    expect(screen.getByText('In der Wiederholung 1 richtig.')).toBeTruthy();

    const answers = calls
      .filter((c) => c.key === 'POST /learn/answer')
      .map((c) => c.body as Record<string, unknown>);
    expect(answers.map((a) => [a.vocabId, a.selfGrade, a.repeat, a.answer])).toEqual([
      [1, false, false, null],
      [2, true, false, null],
      [1, true, true, null],
    ]);
  });

  it('Weg A: getippte falsche Antwort mit „Ich hatte recht“', async () => {
    const calls = api(() => result(false, { decidedBy: 'local', canOverride: true }));
    await start();
    fireEvent.change(screen.getByLabelText('Deine Übersetzung (optional)'), {
      target: { value: 'Kumpel' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen' }));

    expect(await screen.findByText('Falsch')).toBeTruthy();
    expect(screen.getByText('Kumpel')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Ich hatte recht/ }));
    expect(await screen.findByText('Richtig')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Weiter' }));

    // No repetition planned after the correction.
    expect(await screen.findByText('Karte 2 von 2')).toBeTruthy();
    const body = calls.find((c) => c.key === 'POST /learn/answer')!.body as Record<string, unknown>;
    expect(body).toMatchObject({ vocabId: 1, direction: 'foreign_native', answer: 'Kumpel' });
    expect(typeof body.msTotal).toBe('number');
    expect(typeof body.msToFirstKey).toBe('number');
  });

  it('kein „Ich hatte recht“, wenn die KI entschieden hat', async () => {
    api(() => result(false, { decidedBy: 'ai', canOverride: false }));
    await start();
    fireEvent.change(screen.getByLabelText('Deine Übersetzung (optional)'), {
      target: { value: 'Feind' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen' }));
    expect(await screen.findByText('Falsch')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Ich hatte recht/ })).toBeNull();
  });

  it('richtige getippte Antworten gehen automatisch weiter', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    api(() => result(true, { decidedBy: 'typo', typoOf: 'freund' }));
    await start();
    fireEvent.change(screen.getByLabelText('Deine Übersetzung (optional)'), {
      target: { value: 'Fruend' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Prüfen' }));
    expect(
      await screen.findByText('Richtig – kleiner Tippfehler. Schreibweise: freund'),
    ).toBeTruthy();
    await vi.advanceTimersByTimeAsync(1500);
    expect(await screen.findByText('Karte 2 von 2')).toBeTruthy();
    vi.useRealTimers();
  });
});
