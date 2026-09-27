import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { mockApi, renderApp, USER } from './utils';

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

  it('registriert mit Code, Sprache und Zeitzone', async () => {
    const calls = mockApi({
      'GET /auth/me': signedOut,
      'POST /auth/register': () => ({ status: 201, body: { user: USER } }),
    });
    renderApp('/');
    fireEvent.click(await screen.findByRole('button', { name: /Registrieren/ }));
    expect(screen.getByRole('heading', { level: 1, name: 'Account anlegen' })).toBeTruthy();
    type('E-Mail', 'anna@example.org');
    type('Passwort', 'geheim123');
    type('Registrierungscode', 'ABCD-EFGH-JKMN');
    fireEvent.click(screen.getByRole('button', { name: 'Account anlegen' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Heute' })).toBeTruthy();
    const body = calls.find((c) => c.key === 'POST /auth/register')?.body as Record<
      string,
      unknown
    >;
    expect(body).toMatchObject({
      email: 'anna@example.org',
      password: 'geheim123',
      registrationCode: 'ABCD-EFGH-JKMN',
      uiLanguage: 'de',
    });
    expect(typeof body.timezone).toBe('string');
  });
});

describe('Einstellungen: Account', () => {
  it('zeigt nach dem Abmelden die Anmeldeseite', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'POST /auth/logout': () => ({ status: 204 }),
    });
    renderApp('/settings');
    expect(await screen.findByText('anna@example.org')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Abmelden' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Anmelden' })).toBeTruthy();
    expect(calls.map((c) => c.key)).toContain('POST /auth/logout');
    // The UI must not ask the server again: "signed out" is set locally.
    expect(calls.filter((c) => c.key === 'GET /auth/me')).toHaveLength(1);
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
