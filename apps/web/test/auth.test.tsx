import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { mockApi, renderApp, SETTINGS, USER } from './utils';

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const signedOut = () => ({ status: 401, body: { error: 'unauthorized' } });

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

describe('Anmeldung', () => {
  it('zeigt ohne Anmeldung die Anmeldeseite statt der Tabs', async () => {
    mockApi({ 'GET /auth/me': signedOut });
    renderApp('/packages');
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'Pakete' })).toBeNull();
  });

  it('meldet an und zeigt danach die Seite', async () => {
    const calls = mockApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': () => ({ status: 200, body: { user: USER } }),
    });
    renderApp('/packages');
    await screen.findByRole('heading', { name: 'Anmelden' });
    type('E-Mail', 'anna@example.org');
    type('Passwort', 'geheim123');
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Vokabelpakete' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /auth/login')?.body).toEqual({
      email: 'anna@example.org',
      password: 'geheim123',
      client: 'web',
    });
  });

  it('zeigt eine verständliche Fehlermeldung', async () => {
    mockApi({
      'GET /auth/me': signedOut,
      'POST /auth/login': () => ({ status: 401, body: { error: 'invalid_credentials' } }),
    });
    renderApp('/');
    await screen.findByRole('heading', { name: 'Anmelden' });
    type('E-Mail', 'anna@example.org');
    type('Passwort', 'falsch');
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect((await screen.findByRole('alert')).textContent).toBe(
      'E-Mail oder Passwort stimmt nicht.',
    );
  });

  it('bietet keine Registrierung an: Accounts legt der Superuser an', async () => {
    mockApi({ 'GET /auth/me': signedOut });
    renderApp('/');
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Registrieren/ })).toBeNull();
    expect(screen.queryByLabelText('Registrierungscode')).toBeNull();
    expect(screen.getByText('Einen Account bekommst du vom Superuser.')).toBeTruthy();
  });
});

describe('Account-Auswahl', () => {
  const mode = () => ({
    status: 200,
    body: {
      accounts: [
        { email: 'admin@wordflow.com', name: 'Superuser' },
        { email: 'anna@example.org', name: 'Anna' },
      ],
    },
  });

  it('listet die Accounts des Servers und meldet mit gewähltem Account an', async () => {
    const calls = mockApi({
      'GET /auth/me': signedOut,
      'GET /auth/mode': mode,
      'POST /auth/login': () => ({ status: 200, body: { user: USER } }),
    });
    renderApp('/packages');
    fireEvent.click(await screen.findByRole('button', { name: 'Anna · anna@example.org' }));
    expect((screen.getByLabelText('E-Mail') as HTMLInputElement).value).toBe('anna@example.org');
    expect(screen.getByText('Accounts auf diesem Server')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Superuser · admin@wordflow.com' })).toBeTruthy();
    type('Passwort', 'geheim123');
    fireEvent.click(screen.getByRole('button', { name: 'Anmelden' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Vokabelpakete' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /auth/login')?.body).toMatchObject({
      email: 'anna@example.org',
      password: 'geheim123',
    });
  });
});

describe('Einstellungen: Account', () => {
  it('meldet über den Button oben rechts ab und zeigt die Anmeldeseite', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
      'POST /auth/logout': () => ({ status: 204 }),
    });
    renderApp('/settings');
    expect(await screen.findByText('anna@example.org')).toBeTruthy();
    // Desktop and mobile header both have it; the settings page itself does not.
    const buttons = screen.getAllByRole('button', { name: 'Abmelden' });
    expect(buttons).toHaveLength(2);
    expect(buttons.every((b) => b.closest('header'))).toBe(true);
    fireEvent.click(buttons[0]!);
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(calls.map((c) => c.key)).toContain('POST /auth/logout');
    // The UI must not ask the server again: "signed out" is set locally.
    expect(calls.filter((c) => c.key === 'GET /auth/me')).toHaveLength(1);
    expect(calls.filter((c) => c.key === 'GET /settings')).toHaveLength(1);
  });

  it('löscht den Account nach Passwort-Bestätigung', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'DELETE /auth/me': () => ({ status: 204 }),
    });
    renderApp('/settings');
    await screen.findByText('anna@example.org');
    fireEvent.click(screen.getByRole('button', { name: 'Account löschen …' }));
    type('Passwort zur Bestätigung', 'geheim123');
    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'DELETE /auth/me')?.body).toEqual({
      password: 'geheim123',
    });
  });

  it('zeigt beim Löschen ein falsches Passwort an', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'DELETE /auth/me': () => ({ status: 403, body: { error: 'wrong_password' } }),
    });
    renderApp('/settings');
    await screen.findByText('anna@example.org');
    fireEvent.click(screen.getByRole('button', { name: 'Account löschen …' }));
    type('Passwort zur Bestätigung', 'falsch');
    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }));
    expect(await screen.findByText('Das aktuelle Passwort stimmt nicht.')).toBeTruthy();
  });

  it('ändert das Passwort', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'POST /auth/password': () => ({ status: 204 }),
    });
    renderApp('/settings');
    await screen.findByText('anna@example.org');
    type('Aktuelles Passwort', 'geheim123');
    type('Neues Passwort', 'neuesPasswort');
    fireEvent.click(screen.getByRole('button', { name: 'Passwort ändern' }));
    expect((await screen.findByRole('status')).textContent).toContain('Passwort geändert');
    expect(calls.find((c) => c.key === 'POST /auth/password')?.body).toEqual({
      currentPassword: 'geheim123',
      newPassword: 'neuesPasswort',
    });
  });

  it('Logo und Name führen zur Startseite', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /settings': () => ({ status: 200, body: { settings: SETTINGS } }),
    });
    renderApp('/settings');
    await screen.findByText('anna@example.org');
    for (const link of screen.getAllByRole('link', { name: 'Zur Startseite' }))
      expect(link.getAttribute('href')).toBe('/');
  });

  it('speichert die Sprache im Account', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'PATCH /auth/me': (body) => ({
        status: 200,
        body: { user: { ...USER, ...(body as object) } },
      }),
    });
    renderApp('/settings');
    await screen.findByText('anna@example.org');
    fireEvent.click(screen.getByRole('radio', { name: 'English' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Settings' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'PATCH /auth/me')?.body).toEqual({ uiLanguage: 'en' });
  });
});
