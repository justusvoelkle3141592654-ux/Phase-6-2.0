import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { mockApi, renderApp, SETTINGS, USER } from './utils';

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const NEW_USER = { ...USER, setupCompleted: false };

function api() {
  return mockApi({
    'GET /auth/me': () => ({ status: 200, body: { user: NEW_USER } }),
    'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
    'PATCH /auth/me': (body) => ({
      status: 200,
      body: { user: { ...NEW_USER, ...(body as object) } },
    }),
    'PATCH /settings': (body) => ({
      status: 200,
      body: { settings: { ...SETTINGS, ...(body as object) } },
    }),
    'POST /setup/complete': () => ({
      status: 200,
      body: { user: { ...NEW_USER, setupCompleted: true } },
    }),
  });
}

async function next() {
  fireEvent.click(screen.getByRole('button', { name: /^(Weiter|Fertig)$/ }));
}

async function expectStep(n: number, title: string) {
  expect(await screen.findByRole('heading', { level: 1, name: title })).toBeTruthy();
  expect(screen.getByText(`Schritt ${n} von 10`)).toBeTruthy();
}

describe('Einrichtungsassistent', () => {
  it('erscheint nach der Registrierung statt der Tabs', async () => {
    api();
    renderApp('/');
    await expectStep(1, 'Sprache');
    expect(screen.queryByRole('link', { name: 'Start' })).toBeNull();
  });

  it('führt durch alle zehn Schritte und speichert die Eingaben', async () => {
    const calls = api();
    renderApp('/');

    await expectStep(1, 'Sprache');
    await next();

    await expectStep(2, 'Name');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Anna' } });
    await next();

    await expectStep(3, 'Zeitzone');
    fireEvent.change(screen.getByLabelText('Zeitzone'), { target: { value: 'Europe/Vienna' } });
    await next();

    await expectStep(4, 'KI für die Antwortprüfung');
    expect(screen.getByText('Pollinations.ai')).toBeTruthy();
    await next();

    await expectStep(5, 'Modell für die Antwortprüfung');
    expect(screen.getByText('openai/gpt-oss-20b')).toBeTruthy();
    await next();

    await expectStep(6, 'KI für die Tageszusammenfassung');
    await next();

    await expectStep(7, 'Bilderkennung');
    await next();

    await expectStep(8, 'Zeitlimit der KI-Prüfung');
    fireEvent.change(screen.getByLabelText('Zeitlimit'), { target: { value: '3' } });
    await next();

    await expectStep(9, 'Lernen');
    fireEvent.change(screen.getByLabelText('Stufe 2'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('radio', { name: 'Eine Stufe zurück' }));
    await next();

    await expectStep(10, 'Fertig');
    const summary = screen.getByRole('heading', { level: 1, name: 'Fertig' }).parentElement!;
    expect(within(summary).getByText('Anna')).toBeTruthy();
    expect(within(summary).getByText('3 · 10 · 20 · 40 · 80 Tage')).toBeTruthy();
    await next();

    expect(await screen.findByRole('heading', { level: 1, name: 'Heute' })).toBeTruthy();

    const bodies = (key: string) => calls.filter((c) => c.key === key).map((c) => c.body);
    expect(bodies('PATCH /auth/me')).toEqual(
      expect.arrayContaining([
        { uiLanguage: 'de' },
        { name: 'Anna' },
        { timezone: 'Europe/Vienna' },
      ]),
    );
    expect(bodies('PATCH /settings')).toEqual([
      { aiTimeoutMs: 3000 },
      { intervals: [3, 10, 20, 40, 80], wrongMode: 'back' },
    ]);
    expect(bodies('POST /setup/complete')).toHaveLength(1);
  });

  it('lässt ungültige Intervalle nicht zu', async () => {
    api();
    localStorage.setItem(`gero.setupStep.${USER.id}`, '8');
    renderApp('/');
    await expectStep(9, 'Lernen');
    fireEvent.change(screen.getByLabelText('Stufe 4'), { target: { value: '0' } });
    expect((screen.getByRole('button', { name: 'Weiter' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('geht mit „Zurück“ einen Schritt zurück', async () => {
    api();
    renderApp('/');
    await expectStep(1, 'Sprache');
    await next();
    await expectStep(2, 'Name');
    fireEvent.click(screen.getByRole('button', { name: 'Zurück' }));
    await expectStep(1, 'Sprache');
  });

  it('schaltet die Sprache sofort um', async () => {
    const calls = api();
    renderApp('/');
    await expectStep(1, 'Sprache');
    fireEvent.click(screen.getByRole('radio', { name: 'English' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Language' })).toBeTruthy();
    expect(screen.getByText('Step 1 of 10')).toBeTruthy();
    expect(calls.find((c) => c.key === 'PATCH /auth/me')?.body).toEqual({ uiLanguage: 'en' });
  });
});
