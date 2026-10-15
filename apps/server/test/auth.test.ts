import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { ORIGIN, register, sessionCookie, testApp, TEST_CODE } from './helpers';

let app: FastifyInstance;
beforeEach(async () => {
  app = await testApp();
});
afterEach(async () => {
  await app.close();
});

const json = { origin: ORIGIN };

function me(headers: Record<string, string>) {
  return app.inject({ method: 'GET', url: '/api/auth/me', headers });
}

function login(payload: Record<string, unknown>) {
  return app.inject({ method: 'POST', url: '/api/auth/login', headers: json, payload });
}

describe('Registrierung', () => {
  it('legt einen Account an und meldet per Cookie an', async () => {
    const res = await register(app, { uiLanguage: 'en', timezone: 'Europe/Vienna' });
    expect(res.statusCode).toBe(201);
    expect(res.json().user).toMatchObject({
      email: 'anna@example.org',
      uiLanguage: 'en',
      timezone: 'Europe/Vienna',
      setupCompleted: false,
    });
    expect(res.json().token).toBeUndefined();

    const cookie = res.cookies.find((c) => c.name === 'wordflow_session');
    expect(cookie).toMatchObject({ httpOnly: true, sameSite: 'Lax', path: '/' });
    expect(cookie?.secure).toBeFalsy();

    const who = await me({ cookie: `wordflow_session=${cookie!.value}` });
    expect(who.statusCode).toBe(200);
    expect(who.json().user.email).toBe('anna@example.org');
  });

  it('speichert E-Mails klein geschrieben und ohne Leerzeichen', async () => {
    const res = await register(app, { email: '  Anna@Example.ORG ' });
    expect(res.json().user.email).toBe('anna@example.org');
  });

  it('lehnt einen falschen Registrierungscode ab', async () => {
    const res = await register(app, { registrationCode: 'FALSCH' });
    expect(res.statusCode).toBe(403);
    expect(res.json()).toEqual({ error: 'invalid_registration_code' });
  });

  it('akzeptiert den Code unabhängig von Groß-/Kleinschreibung', async () => {
    const res = await register(app, { registrationCode: TEST_CODE.toLowerCase() });
    expect(res.statusCode).toBe(201);
  });

  it('lehnt doppelte E-Mail-Adressen ab', async () => {
    await register(app);
    const res = await register(app, { email: 'ANNA@example.org' });
    expect(res.statusCode).toBe(409);
    expect(res.json()).toEqual({ error: 'email_taken' });
  });

  it('prüft E-Mail, Passwortlänge und Zeitzone', async () => {
    const res = await register(app, {
      email: 'keine-mail',
      password: 'kurz',
      timezone: 'Mars/Olymp',
    });
    expect(res.statusCode).toBe(400);
    expect(res.json()).toMatchObject({ error: 'validation_error' });
    expect(res.json().fields).toEqual(expect.arrayContaining(['email', 'password', 'timezone']));
  });
});

describe('Offline-Modus', () => {
  it('meldet ohne Registrierungscode mit Standard-Account an', async () => {
    const localApp = await testApp({ offlineMode: true });
    const info = await localApp.inject({ method: 'GET', url: '/api/auth/mode', headers: json });
    expect(info.statusCode).toBe(200);
    expect(info.json()).toMatchObject({ offlineMode: true, email: 'offline@local.test' });

    const registerRes = await localApp.inject({
      method: 'POST',
      url: '/api/auth/register',
      headers: json,
      payload: {
        email: 'guest@example.org',
        password: 'geheim123',
        uiLanguage: 'de',
        timezone: 'Europe/Berlin',
      },
    });
    expect(registerRes.statusCode).toBe(201);

    const loginRes = await localApp.inject({
      method: 'POST',
      url: '/api/auth/login',
      headers: json,
      payload: { email: 'offline@local.test', password: 'offline' },
    });
    expect(loginRes.statusCode).toBe(200);
    expect(sessionCookie(loginRes)).toBeTruthy();
    await localApp.close();
  });
});

describe('Anmeldung', () => {
  beforeEach(async () => {
    await register(app);
  });

  it('meldet mit richtigem Passwort an', async () => {
    const res = await login({ email: 'anna@example.org', password: 'geheim123' });
    expect(res.statusCode).toBe(200);
    expect(sessionCookie(res)).toBeTruthy();
  });

  it('lehnt falsches Passwort und unbekannte E-Mail gleich ab', async () => {
    const wrong = await login({ email: 'anna@example.org', password: 'falsch123' });
    const unknown = await login({ email: 'bob@example.org', password: 'geheim123' });
    expect(wrong.statusCode).toBe(401);
    expect(unknown.statusCode).toBe(401);
    expect(wrong.json()).toEqual({ error: 'invalid_credentials' });
    expect(unknown.json()).toEqual(wrong.json());
  });

  it('gibt der App einen Bearer-Token statt eines Cookies', async () => {
    const res = await login({ email: 'anna@example.org', password: 'geheim123', client: 'app' });
    expect(res.statusCode).toBe(200);
    expect(sessionCookie(res)).toBeUndefined();
    const token = res.json().token as string;
    expect(token.length).toBeGreaterThanOrEqual(43);
    expect((await me({ authorization: `Bearer ${token}` })).statusCode).toBe(200);
  });

  it('ohne Anmeldung liefert /auth/me 401', async () => {
    expect((await me({})).statusCode).toBe(401);
    expect((await me({ authorization: 'Bearer unsinn' })).statusCode).toBe(401);
  });

  it('setzt das Secure-Flag hinter einem HTTPS-Proxy', async () => {
    const proxied = await testApp({ trustProxy: true });
    await register(proxied);
    const res = await proxied.inject({
      method: 'POST',
      url: '/api/auth/login',
      headers: { origin: 'https://localhost:80', 'x-forwarded-proto': 'https' },
      payload: { email: 'anna@example.org', password: 'geheim123' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.cookies.find((c) => c.name === 'wordflow_session')?.secure).toBe(true);
    await proxied.close();
  });
});

describe('Abmelden', () => {
  it('löscht die Session', async () => {
    const cookie = sessionCookie(await register(app))!;
    const headers = { ...json, cookie: `wordflow_session=${cookie}` };
    const res = await app.inject({ method: 'POST', url: '/api/auth/logout', headers });
    expect(res.statusCode).toBe(204);
    const cleared = res.cookies.find((c) => c.name === 'wordflow_session');
    expect(cleared?.value).toBe('');
    expect((await me({ cookie: `wordflow_session=${cookie}` })).statusCode).toBe(401);
  });

  it('beendet auch App-Tokens', async () => {
    await register(app);
    const token = (
      await login({ email: 'anna@example.org', password: 'geheim123', client: 'app' })
    ).json().token as string;
    const headers = { authorization: `Bearer ${token}` };
    await app.inject({ method: 'POST', url: '/api/auth/logout', headers });
    expect((await me(headers)).statusCode).toBe(401);
  });
});

describe('Profil und Passwort', () => {
  let headers: Record<string, string>;
  beforeEach(async () => {
    headers = { ...json, cookie: `wordflow_session=${sessionCookie(await register(app))}` };
  });

  it('ändert Name, Sprache und Zeitzone', async () => {
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/auth/me',
      headers,
      payload: { name: ' Anna ', uiLanguage: 'en', timezone: 'America/New_York' },
    });
    expect(res.statusCode).toBe(200);
    expect(res.json().user).toMatchObject({
      name: 'Anna',
      uiLanguage: 'en',
      timezone: 'America/New_York',
    });
  });

  it('ändert das Passwort und meldet andere Geräte ab', async () => {
    const other = sessionCookie(await login({ email: 'anna@example.org', password: 'geheim123' }))!;

    const wrong = await app.inject({
      method: 'POST',
      url: '/api/auth/password',
      headers,
      payload: { currentPassword: 'falsch', newPassword: 'neuesPasswort' },
    });
    expect(wrong.statusCode).toBe(403);
    expect(wrong.json()).toEqual({ error: 'wrong_password' });

    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/password',
      headers,
      payload: { currentPassword: 'geheim123', newPassword: 'neuesPasswort' },
    });
    expect(res.statusCode).toBe(204);

    expect((await me({ cookie: headers.cookie! })).statusCode).toBe(200);
    expect((await me({ cookie: `wordflow_session=${other}` })).statusCode).toBe(401);
    expect((await login({ email: 'anna@example.org', password: 'geheim123' })).statusCode).toBe(
      401,
    );
    expect((await login({ email: 'anna@example.org', password: 'neuesPasswort' })).statusCode).toBe(
      200,
    );
  });
});

describe('Schutz vor Cross-Site-Anfragen', () => {
  it('lehnt Cookie-Anfragen von fremden Seiten ab', async () => {
    const cookie = sessionCookie(await register(app))!;
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/auth/me',
      headers: { origin: 'https://boese.example', cookie: `wordflow_session=${cookie}` },
      payload: { name: 'gehackt' },
    });
    expect(res.statusCode).toBe(403);
    expect(res.json()).toEqual({ error: 'forbidden_origin' });
  });

  it('erlaubt Vite-Dev-Requests vom selben Host mit anderem Port', async () => {
    await register(app);
    const res = await app.inject({
      method: 'POST',
      url: '/api/auth/login',
      headers: { origin: 'http://192.168.100.59:5173', host: '192.168.100.59:3000' },
      payload: { email: 'anna@example.org', password: 'geheim123' },
    });
    expect(res.statusCode).toBe(200);
    expect(sessionCookie(res)).toBeTruthy();
  });

  it('lehnt Cookie-Anfragen ohne Origin ab', async () => {
    const cookie = sessionCookie(await register(app))!;
    const res = await app.inject({
      method: 'PATCH',
      url: '/api/auth/me',
      headers: { cookie: `wordflow_session=${cookie}` },
      payload: { name: 'x' },
    });
    expect(res.statusCode).toBe(403);
  });

  it('erlaubt Anfragen der Android-App mit CORS, aber ohne Cookies', async () => {
    const res = await app.inject({
      method: 'OPTIONS',
      url: '/api/auth/login',
      headers: {
        origin: 'https://localhost',
        'access-control-request-method': 'POST',
        'access-control-request-headers': 'content-type,authorization',
      },
    });
    expect(res.headers['access-control-allow-origin']).toBe('https://localhost');
    expect(res.headers['access-control-allow-credentials']).toBeUndefined();

    const reg = await register(app, { client: 'app' });
    const login = await app.inject({
      method: 'POST',
      url: '/api/auth/login',
      headers: { origin: 'https://localhost' },
      payload: { email: 'anna@example.org', password: 'geheim123', client: 'app' },
    });
    expect(reg.statusCode).toBe(201);
    expect(login.statusCode).toBe(200);
    expect(login.json().token).toBeTruthy();
  });

  it('gibt fremden Origins keine CORS-Freigabe', async () => {
    const res = await app.inject({
      method: 'OPTIONS',
      url: '/api/auth/login',
      headers: { origin: 'https://boese.example', 'access-control-request-method': 'POST' },
    });
    expect(res.headers['access-control-allow-origin']).toBeUndefined();
  });
});

describe('Rate-Limit', () => {
  it('bremst nach 10 Anmeldeversuchen pro Minute', async () => {
    const codes: number[] = [];
    for (let i = 0; i < 11; i++) {
      codes.push((await login({ email: 'x@example.org', password: 'falsch' })).statusCode);
    }
    expect(codes.slice(0, 10).every((c) => c === 401)).toBe(true);
    expect(codes[10]).toBe(429);
    const res = await login({ email: 'x@example.org', password: 'falsch' });
    expect(res.json()).toEqual({ error: 'rate_limited' });
  });
});
