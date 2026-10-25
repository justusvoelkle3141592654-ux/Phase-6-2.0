import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { ADMIN, mockApi, renderApp, SETTINGS, USER } from './utils';

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

function api(providers: object[] = [POLLINATIONS], user = ADMIN) {
  let list = providers;
  return mockApi({
    'GET /auth/me': () => ({ status: 200, body: { user } }),
    'GET /admin/users': () => ({ status: 200, body: { users: [] } }),
    'GET /admin/update': () => ({
      status: 200,
      body: {
        repo: 'rcode125/Phase-6-2.0',
        branch: 'claude/new-session-uq36ca',
        hasToken: true,
        current: null,
        latest: 'abc1234def',
        available: true,
        running: false,
        log: '',
      },
    }),
    'PUT /admin/update/source': (body) => ({
      status: 200,
      body: { ...(body as object), hasToken: true },
    }),
    'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
    'GET /ai/config': () => ({
      status: 200,
      body: {
        providers: list,
        tasks: {
          check: { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
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
  it('nur der Superuser sieht KI, Accounts, Backup und Update', async () => {
    const calls = api([POLLINATIONS], USER);
    renderApp('/settings?tab=ai');
    // Unknown tabs for this account fall back to "Account".
    expect(await screen.findByRole('heading', { name: 'Passwort ändern' })).toBeTruthy();
    expect(screen.getAllByRole('tab').map((t) => t.textContent)).toEqual(['Account', 'Lernen']);
    expect(screen.queryByText(/Zeitlimit/)).toBeNull();
    expect(calls.some((c) => c.key.startsWith('GET /ai/') || c.key.startsWith('GET /admin/'))).toBe(
      false,
    );
  });

  it('der Superuser sieht Accounts, Backup und Update in eigenen Tabs', async () => {
    const calls = api();
    renderApp('/settings');
    expect((await screen.findAllByRole('tab')).map((t) => t.textContent)).toEqual([
      'Account',
      'Lernen',
      'KI',
      'Accounts',
      'System',
    ]);
    // The superuser's own account cannot be deleted.
    expect(screen.queryByRole('heading', { name: 'Account löschen' })).toBeNull();
    fireEvent.click(screen.getByRole('tab', { name: 'Accounts' }));
    expect(await screen.findByRole('heading', { level: 2, name: 'Accounts' })).toBeTruthy();
    fireEvent.click(screen.getByRole('tab', { name: 'System' }));
    expect(screen.getByRole('heading', { level: 2, name: 'Datenbank sichern' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Exportieren/ })).toBeTruthy();
    expect(await screen.findByText('Eine neue Version ist verfügbar.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Aktualisieren/ })).toBeTruthy();

    // Repository, branch and token can be changed; the token is never shown.
    const repo = screen.getByLabelText('Repository') as HTMLInputElement;
    expect(repo.value).toBe('rcode125/Phase-6-2.0');
    expect((screen.getByLabelText('GitHub-Token') as HTMLInputElement).value).toBe('');
    expect(screen.getByText(/Ein Token ist gespeichert/)).toBeTruthy();
    fireEvent.change(repo, { target: { value: 'https://github.com/me/fork' } });
    fireEvent.change(screen.getByLabelText('Branch'), { target: { value: 'main' } });
    fireEvent.change(screen.getByLabelText('GitHub-Token'), {
      target: { value: 'github_pat_neu' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }));
    expect(await screen.findByText('Gespeichert.')).toBeTruthy();
    expect(calls.find((c) => c.key === 'PUT /admin/update/source')?.body).toEqual({
      repo: 'https://github.com/me/fork',
      branch: 'main',
      token: 'github_pat_neu',
    });
  });

  it('zeigt Schlüssel nur als Hinweis', async () => {
    api([POLLINATIONS, CLAUDE]);
    renderApp('/settings?tab=ai');
    expect(await screen.findByText('Gesetzt · …9876')).toBeTruthy();
    expect(screen.getAllByText('Kein Schlüssel').length).toBeGreaterThan(0);
  });

  it('erklärt bei Pollinations 401, wo es einen Schlüssel gibt', async () => {
    api();
    renderApp('/settings?tab=ai');
    const row = (
      await screen.findByText('https://gen.pollinations.ai/v1 · OpenAI-kompatibel')
    ).closest('li')!;
    fireEvent.click(within(row).getByRole('button', { name: 'Verbindung testen' }));
    const alert = await within(row).findByRole('alert');
    expect(alert.textContent).toContain('gültigen API-Schlüssel (Fehler 401)');
    expect(alert.textContent).toContain('enter.pollinations.ai');
  });

  it('fügt eine API mit Schlüssel und Bezeichnung hinzu', async () => {
    const calls = api();
    renderApp('/settings?tab=ai');
    fireEvent.click(await screen.findByRole('button', { name: 'API hinzufügen' }));
    fireEvent.change(document.getElementById('provider-name')!, {
      target: { value: 'Claude Bilder' },
    });
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
      name: 'Claude Bilder',
      apiKey: 'sk-ant-9876',
    });
  });

  it('Bilderkennung bekommt eine eigene neue API, getrennt von der Vokabelabfrage', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
      'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
      'GET /ai/config': () => ({
        status: 200,
        body: {
          providers: added ? [POLLINATIONS, CLAUDE] : [POLLINATIONS],
          tasks: {
            check: { providerId: 1, model: 'openai/gpt-oss-20b', reasoning: null },
            vision: null,
          },
        },
      }),
      'GET /ai/latency': () => ({ status: 200, body: { stats: [] } }),
      'GET /ai/providers/1/models': () => ({ status: 200, body: { models: [] } }),
      'GET /ai/providers/1/models?vision=1': () => ({ status: 200, body: { models: [] } }),
      'GET /ai/providers/2/models?vision=1': () => ({
        status: 200,
        body: { models: [{ id: 'claude-x', vision: true, reasoning: false }] },
      }),
      'POST /ai/providers': () => {
        added = true;
        return { status: 201, body: { provider: CLAUDE } };
      },
      'PUT /ai/tasks/vision': (body) => ({ status: 200, body: { task: body } }),
    });
    let added = false;
    renderApp('/settings?tab=ai');
    const visionRow = (await screen.findByText('Bilderkennung', { selector: 'p' })).closest('li')!;
    fireEvent.change(within(visionRow).getByLabelText('Anbieter'), { target: { value: 'new' } });
    fireEvent.change(within(visionRow).getByLabelText('Anbieter'), {
      target: { value: 'anthropic' },
    });
    fireEvent.change(within(visionRow).getByLabelText('API-Schlüssel'), {
      target: { value: 'sk-ant-9876' },
    });
    fireEvent.click(within(visionRow).getByRole('button', { name: 'Hinzufügen' }));
    expect(
      await within(visionRow).findByText('API-Schlüssel von „Anthropic (Claude)“'),
    ).toBeTruthy();
    await within(visionRow).findByRole('option', { name: 'claude-x' });
    // The last "Speichern" saves the task (the one before belongs to the key).
    fireEvent.click(within(visionRow).getAllByRole('button', { name: 'Speichern' }).at(-1)!);
    await vi.waitFor(() =>
      expect(calls.find((c) => c.key === 'PUT /ai/tasks/vision')?.body).toEqual({
        providerId: 2,
        model: 'claude-x',
        reasoning: false,
      }),
    );
  });
});
