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
      wrongMode: 'back',
      aiTimeoutMs: 2000,
      autoNextCorrectMs: 3000,
      autoNextTypoMs: 3000,
      autoNextWrongMs: 3000,
      dailyLimit: 30,
    });
  });

  it('speichert Intervalle, Fehler-Modus, Zeitlimit, Weiter-Zeiten und Tageslimit', async () => {
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: {
        intervals: [1, 3, 7, 14, 30],
        wrongMode: 'reset',
        aiTimeoutMs: 3000,
        autoNextCorrectMs: 0,
        autoNextTypoMs: 4000,
        autoNextWrongMs: 10000,
        dailyLimit: 50,
      },
    });
    expect(res.statusCode).toBe(200);
    const again = await app.inject({ method: 'GET', url: '/api/settings', headers });
    expect(again.json().settings).toEqual({
      intervals: [1, 3, 7, 14, 30],
      wrongMode: 'reset',
      aiTimeoutMs: 3000,
      autoNextCorrectMs: 0,
      autoNextTypoMs: 4000,
      autoNextWrongMs: 10000,
      dailyLimit: 50,
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
        autoNextTypoMs: 20000,
        autoNextCorrectMs: 1500,
        dailyLimit: 0,
      },
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().fields).toEqual(
      expect.arrayContaining([
        'intervals',
        'wrongMode',
        'aiTimeoutMs',
        'autoNextTypoMs',
        'autoNextCorrectMs',
        'dailyLimit',
      ]),
    );
  });

  it('trennt die Einstellungen der Accounts', async () => {
    await app.inject({
      method: 'PATCH',
      url: '/api/settings',
      headers,
      payload: { wrongMode: 'reset' },
    });
    const other = sessionCookie(await register(app, { email: 'bob@example.org' }))!;
    const res = await app.inject({
      method: 'GET',
      url: '/api/settings',
      headers: { cookie: `wordflow_session=${other}` },
    });
    expect(res.json().settings.wrongMode).toBe('back');
  });

  it('braucht eine Anmeldung', async () => {
    const res = await app.inject({ method: 'GET', url: '/api/settings' });
    expect(res.statusCode).toBe(401);
  });
});
