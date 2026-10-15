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
const POLLINATIONS = {
  id: 1,
  kind: 'pollinations',
  name: 'Pollinations.ai',
  baseUrl: 'https://gen.pollinations.ai/v1',
  protocol: 'openai',
  hasKey: false,
  keyHint: null,
};
const AI_CONFIG = {
  providers: [POLLINATIONS],
  tasks: {
    check: { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
    summary: { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
    vision: null,
  },
};
const MODELS = [
  { id: 'openai/gpt-oss-20b', vision: false, reasoning: true },
  { id: 'google/gemini-3.7-flash', vision: true, reasoning: null },
];

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
    'GET /ai/config': () => ({ status: 200, body: AI_CONFIG }),
    'GET /ai/providers/1/models': () => ({ status: 200, body: { models: MODELS } }),
    'GET /ai/providers/1/models?vision=1': () => ({
      status: 200,
      body: { models: MODELS.filter((m) => m.vision) },
    }),
    'PUT /ai/tasks/check': () => ({ status: 200, body: AI_CONFIG }),
    'PUT /ai/tasks/summary': () => ({ status: 200, body: AI_CONFIG }),
    'PUT /ai/tasks/vision': () => ({ status: 200, body: AI_CONFIG }),
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
    expect(
      screen
        .getByRole('radio', { name: 'Pollinations.ai · Voreinstellung' })
        .getAttribute('aria-checked'),
    ).toBe('true');
    await next();

    await expectStep(5, 'Modell für die Antwortprüfung');
    const model = (await screen.findByRole('option', {
      name: 'openai/gpt-oss-20b',
    })) as HTMLOptionElement;
    expect(model.selected).toBe(true);
    await next();

    await expectStep(6, 'KI für die Tageszusammenfassung');
    expect(
      screen
        .getByRole('radio', { name: /Wie bei der Antwortprüfung/ })
        .getAttribute('aria-checked'),
    ).toBe('true');
    await next();

    await expectStep(7, 'Bilderkennung');
    const vision = (await screen.findByRole('option', {
      name: 'google/gemini-3.7-flash',
    })) as HTMLOptionElement;
    await screen.findByDisplayValue('google/gemini-3.7-flash');
    expect(vision.selected).toBe(true);
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
    expect(bodies('PUT /ai/tasks/check')).toEqual([
      { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
    ]);
    expect(bodies('PUT /ai/tasks/summary')).toEqual([
      { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
    ]);
    expect(bodies('PUT /ai/tasks/vision')).toEqual([
      { providerId: 1, model: 'google/gemini-3.7-flash', reasoning: null },
    ]);
  });

  it('lässt ungültige Intervalle nicht zu', async () => {
    api();
    localStorage.setItem(`wordflow.setupStep.${USER.id}`, '8');
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
