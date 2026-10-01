import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { renderApp, USER } from './utils';

// Simulate the Android app: platform module with in-memory storage.
const store = vi.hoisted(() => ({
  serverUrl: null as string | null,
  token: null as string | null,
}));
vi.mock('../src/lib/platform', () => ({
  isApp: true,
  initPlatform: async () => undefined,
  getServerUrl: () => store.serverUrl,
  setServerUrl: async (url: string) => {
    store.serverUrl = url;
    store.token = null;
  },
  getToken: () => store.token,
  setToken: async (value: string | null) => {
    store.token = value;
  },
  normalizeServerUrl: (input: string) => {
    const trimmed = input.trim().replace(/\/+$/, '');
    return /^https?:\/\//i.test(trimmed) ? trimmed : `http://${trimmed}`;
  },
}));

type Call = {
  url: string;
  method: string;
  auth: string | null;
  credentials?: RequestCredentials;
  body?: unknown;
};
let calls: Call[];

beforeEach(() => {
  localStorage.clear();
  store.serverUrl = null;
  store.token = null;
  calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const headers = new Headers(init?.headers);
      calls.push({
        url,
        method: init?.method ?? 'GET',
        auth: headers.get('Authorization'),
        credentials: init?.credentials,
        body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
      });
      const json = (status: number, body: unknown) =>
        new Response(JSON.stringify(body), {
          status,
          headers: { 'Content-Type': 'application/json' },
        });
      if (url === 'http://192.168.1.20:3000/api/health')
        return json(200, { status: 'ok', name: 'WordFlow', version: '0.1.0' });
      if (url === 'http://192.168.1.20:3000/api/auth/login')
        return json(200, { user: USER, token: 'tok123' });
      if (url === 'http://192.168.1.20:3000/api/overview') return json(404, { error: 'not_found' });
      if (url.startsWith('http://192.168.1.99')) throw new TypeError('Failed to fetch');
      return json(404, { error: 'not_found' });
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('Android-App', () => {
  it('fragt zuerst nach der Server-Adresse und meldet mit Token an', async () => {
    renderApp('/');
    expect(await screen.findByRole('heading', { name: 'Server verbinden' })).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Server-Adresse'), {
      target: { value: '192.168.1.99:3000' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Verbinden' }));
    expect(await screen.findByText(/Server nicht erreichbar/)).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Server-Adresse'), {
      target: { value: '192.168.1.20:3000/' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Verbinden' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(screen.getByText('Server: http://192.168.1.20:3000')).toBeTruthy();
    expect(store.serverUrl).toBe('http://192.168.1.20:3000');

    fireEvent.change(screen.getByLabelText('E-Mail'), { target: { value: 'anna@example.org' } });
    fireEvent.change(screen.getByLabelText('Passwort'), { target: { value: 'geheim123' } });
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Heute' })).toBeTruthy();

    const login = calls.find((c) => c.url.endsWith('/api/auth/login'))!;
    expect(login.body).toMatchObject({ client: 'app' });
    expect(login.credentials).toBe('omit');
    expect(store.token).toBe('tok123');
    const overview = calls.find((c) => c.url.endsWith('/api/overview'))!;
    expect(overview.auth).toBe('Bearer tok123');
  });
});
