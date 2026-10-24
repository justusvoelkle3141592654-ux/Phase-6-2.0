import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import type { OverviewDto } from '@wordflow/shared';
import { mockApi, renderApp, USER } from './utils';

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const STATS = {
  attempts: 0,
  correct: 0,
  wrong: 0,
  accuracy: null,
  medianMsToFirstKey: null,
  medianMsTotal: null,
  stageUps: 0,
  learnedToday: 0,
  difficult: [],
};
const BASE: OverviewDto = {
  today: '2026-09-27',
  due: 12,
  stages: [5, 3, 2, 1, 0, 1],
  learned: 4,
  inactive: 7,
  nextDue: null,
  todayStats: STATS,
};

describe('Startseite', () => {
  it('zeigt Fällige, Stufen und Gelernte ohne KI', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: { ...USER, name: 'Anna' } } }),
      'GET /overview': () => ({ status: 200, body: BASE }),
    });
    renderApp('/');
    expect(await screen.findByRole('heading', { level: 1, name: 'Hallo, Anna' })).toBeTruthy();
    expect(await screen.findByText('12 Vokabeln fällig')).toBeTruthy();
    expect(screen.getByRole('link', { name: /Jetzt lernen/ })).toBeTruthy();
    expect(screen.getByLabelText('Stufe 1: 5')).toBeTruthy();
    expect(screen.getByLabelText('Gelernt: 4')).toBeTruthy();
    expect(screen.getByText('Inaktiv: 7')).toBeTruthy();
    // The daily AI summary was removed.
    expect(screen.queryByText('Tageszusammenfassung')).toBeNull();
    expect(calls.some((c) => c.key === 'GET /overview/summary')).toBe(false);
  });

  it('zeigt Tageswerte und schwierige Vokabeln', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /overview': () => ({
        status: 200,
        body: {
          ...BASE,
          due: 0,
          nextDue: '2026-10-02',
          todayStats: {
            ...STATS,
            attempts: 20,
            correct: 15,
            wrong: 5,
            accuracy: 0.75,
            medianMsTotal: 3400,
            difficult: [{ vocabId: 1, word: 'bellum', translation: 'Krieg', wrong: 3 }],
          },
        },
      }),
    });
    renderApp('/');
    expect(await screen.findByText('Heute ist nichts mehr fällig.')).toBeTruthy();
    expect(screen.getByText(/Freitag, 2. Oktober 2026/)).toBeTruthy();
    expect(screen.getByText('75 %')).toBeTruthy();
    expect(screen.getByText('3,4 s')).toBeTruthy();
    expect(screen.getByText('3× falsch')).toBeTruthy();
  });

  it('ohne aktivierte Vokabeln: Hinweis auf die Pakete', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /overview': () => ({
        status: 200,
        body: { ...BASE, due: 0, stages: [0, 0, 0, 0, 0, 0], learned: 0, inactive: 30 },
      }),
    });
    renderApp('/');
    expect(await screen.findByText(/Noch keine Vokabeln aktiviert/)).toBeTruthy();
    expect(screen.getByRole('link', { name: /Zu den Paketen/ }).getAttribute('href')).toBe(
      '/packages',
    );
  });
});
