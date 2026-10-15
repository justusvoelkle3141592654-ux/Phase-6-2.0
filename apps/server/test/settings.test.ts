import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { ORIGIN, register, sessionCookie, testApp } from './helpers';

let app: FastifyInstance;
let headers: Record<string, string>;
beforeEach(async () => {
  app = await testApp();
  headers = { origin: ORIGIN, cookie: `wordflow_session=${sessionCookie(await register(app))}` };
});
afterEach(async () => {
  await app.close();
});

describe('Einstellungen', () => {
  it('liefert die Standardwerte', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/settings', headers });
    expect(res.statusCode).toBe(200);
    expect(res.json().settings).toEqual({
      intervals: [5, 10, 20, 40, 80],
      wrongMode: 'reset',
      aiTimeoutMs: 2000,
      reminderTime: null,
    });
  });

  it('speichert Intervalle, Fehler-Modus, Zeitlimit und Erinnerung', async () => {
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: {
        intervals: [1, 3, 7, 14, 30],
        wrongMode: 'back',
        aiTimeoutMs: 3000,
        reminderTime: '18:30',
      },
    });
    expect(res.statusCode).toBe(200);
    const again = await app.inject({ method: 'GET', url: '/api/settings', headers });
    expect(again.json().settings).toEqual({
      intervals: [1, 3, 7, 14, 30],
      wrongMode: 'back',
      aiTimeoutMs: 3000,
      reminderTime: '18:30',
    });
  });

  it('lehnt ungültige Werte ab', async () => {
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: {
        intervals: [5, 10, 0],
        wrongMode: 'egal',
        aiTimeoutMs: 100,
        reminderTime: '25:00',
      },
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().fields).toEqual(
      expect.arrayContaining(['intervals', 'wrongMode', 'aiTimeoutMs', 'reminderTime']),
    );
  });

  it('trennt die Einstellungen der Accounts', async () => {
    await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: { wrongMode: 'back' },
    });
    const other = sessionCookie(await register(app, { email: 'bob@example.org' }))!;
    const res = await app.inject({
      method: 'GET',
      url: '/api/settings',
      headers: { cookie: `wordflow_session=${other}` },
    });
    expect(res.json().settings.wrongMode).toBe('reset');
  });

  it('braucht eine Anmeldung', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/settings' });
    expect(res.statusCode).toBe(401);
  });
});

describe('Einrichtung', () => {
  it('markiert die Einrichtung als abgeschlossen', async () => {
    const me = await app.inject({ method: 'GET', url: '/api/auth/me', headers });
    expect(me.json().user.setupCompleted).toBe(false);
    const res = await app.inject({ method: 'POST', url: '/api/setup/complete', headers });
    expect(res.statusCode).toBe(200);
    expect(res.json().user.setupCompleted).toBe(true);
    const after = await app.inject({ method: 'GET', url: '/api/auth/me', headers });
    expect(after.json().user.setupCompleted).toBe(true);
  });
});
