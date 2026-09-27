import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { mockApi, renderApp, SETTINGS, USER } from './utils';

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const POLLINATIONS = {
  id: 1,
  kind: 'pollinations',
  name: 'Pollinations.ai',
  baseUrl: 'https://gen.pollinations.ai/v1',
  protocol: 'openai',
  hasKey: false,
  keyHint: null,
};
const CLAUDE = {
  id: 2,
  kind: 'anthropic',
  name: 'Anthropic (Claude)',
  baseUrl: 'https://api.anthropic.com/v1',
  protocol: 'anthropic',
  hasKey: true,
  keyHint: '…9876',
};

function api(providers: object[] = [POLLINATIONS]) {
  let list = providers;
  return mockApi({
    'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
    'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
    'GET /ai/config': () => ({
      status: 200,
      body: {
        providers: list,
        tasks: {
          check: { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
          summary: null,
          vision: null,
        },
      },
    }),
    'GET /ai/latency': () => ({ status: 200, body: { stats: [] } }),
    'GET /ai/providers/1/models': () => ({
      status: 502,
      body: {
        error: 'provider_error',
        provider: { code: 'auth', status: 401, message: 'Missing key' },
      },
    }),
    'GET /ai/providers/1/models?vision=1': () => ({ status: 200, body: { models: [] } }),
    'GET /ai/providers/2/models': () => ({ status: 200, body: { models: [] } }),
    'POST /ai/providers/1/test': () => ({
      status: 200,
      body: { ok: false, error: { code: 'auth', status: 401, message: 'Missing key' } },
    }),
    'POST /ai/providers': () => {
      list = [...list, CLAUDE];
      return { status: 201, body: { provider: CLAUDE } };
    },
  });
}

describe('KI-Einstellungen', () => {
  it('zeigt Schlüssel nur als Hinweis', async () => {
    api([POLLINATIONS, CLAUDE]);
    renderApp('/settings');
    expect(await screen.findByText('Gesetzt · …9876')).toBeTruthy();
    expect(screen.getAllByText('Kein Schlüssel').length).toBeGreaterThan(0);
  });

  it('erklärt bei Pollinations 401, wo es einen Schlüssel gibt', async () => {
    api();
    renderApp('/settings');
    const row = (
      await screen.findByText('https://gen.pollinations.ai/v1 · OpenAI-kompatibel')
    ).closest('li')!;
    fireEvent.click(within(row).getByRole('button', { name: 'Verbindung testen' }));
    const alert = await within(row).findByRole('alert');
    expect(alert.textContent).toContain('gültigen API-Schlüssel (Fehler 401)');
    expect(alert.textContent).toContain('enter.pollinations.ai');
  });

  it('fügt einen Anbieter mit Schlüssel hinzu', async () => {
    const calls = api();
    renderApp('/settings');
    fireEvent.click(await screen.findByRole('button', { name: 'Anbieter hinzufügen' }));
    fireEvent.change(document.getElementById('provider-kind')!, { target: { value: 'anthropic' } });
    const add = screen.getByRole('button', { name: 'Hinzufügen' }) as HTMLButtonElement;
    expect(add.disabled).toBe(true); // key required
    fireEvent.change(document.getElementById('provider-key')!, {
      target: { value: 'sk-ant-9876' },
    });
    fireEvent.click(add);
    expect(await screen.findByText('Gesetzt · …9876')).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /ai/providers')?.body).toEqual({
      kind: 'anthropic',
      apiKey: 'sk-ant-9876',
    });
  });
});
