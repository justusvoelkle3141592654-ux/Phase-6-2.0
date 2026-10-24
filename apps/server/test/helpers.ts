import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import type { FastifyInstance, LightMyRequestResponse } from 'fastify';
import { buildApp } from '../src/app';
import { loadConfig, type AppConfig } from '../src/config';

export const TEST_CODE = 'TEST-CODE-1234';
export const ORIGIN = 'http://localhost:80';

/** App with an in-memory database and a throw-away data directory. */
export async function testApp(overrides: Partial<AppConfig> = {}): Promise<FastifyInstance> {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-test-'));
  const app = await buildApp({
    ...loadConfig({}),
    logLevel: 'silent',
    webDist: null,
    dataDir,
    dbFile: ':memory:',
    registrationCode: TEST_CODE,
    appSecret: 'test-secret',
    ...overrides,
  });
  app.addHook('onClose', async () => fs.rmSync(dataDir, { recursive: true, force: true }));
  return app;
}

/** Extracts the `wordflow_session` cookie value from a response. */
export function sessionCookie(res: LightMyRequestResponse): string | undefined {
  return res.cookies.find((c) => c.name === 'wordflow_session')?.value;
}

export const ADMIN = { email: 'admin@wordflow.com', password: 'admin' };

async function login(
  app: FastifyInstance,
  email: string,
  password: string,
  client: 'web' | 'app' = 'web',
): Promise<LightMyRequestResponse> {
  return app.inject({
    method: 'POST',
    url: '/api/auth/login',
    headers: { origin: ORIGIN },
    payload: { email, password, client },
  });
}

const adminCookies = new WeakMap<FastifyInstance, string>();

/** Session cookie of the superuser (created on every start); cached per app (login is rate-limited). */
export async function adminCookie(app: FastifyInstance): Promise<string> {
  const cached = adminCookies.get(app);
  if (cached) return cached;
  const res = await login(app, ADMIN.email, ADMIN.password);
  const cookie = sessionCookie(res);
  if (!cookie) throw new Error(`admin login failed: ${res.statusCode}`);
  adminCookies.set(app, cookie);
  return cookie;
}

/** Request headers signed in as the superuser. */
export async function adminHeaders(app: FastifyInstance) {
  return { origin: ORIGIN, cookie: `wordflow_session=${await adminCookie(app)}` };
}

/**
 * Creates a normal account through the superuser and signs it in, like the old
 * self-registration did. Returns the login response.
 */
export async function register(
  app: FastifyInstance,
  body: {
    email?: string;
    password?: string;
    name?: string;
    uiLanguage?: 'de' | 'en';
    timezone?: string;
    client?: 'web' | 'app';
  } = {},
): Promise<LightMyRequestResponse> {
  const email = body.email ?? 'anna@example.org';
  const password = body.password ?? 'geheim123';
  const created = await app.inject({
    method: 'POST',
    url: '/api/admin/users',
    headers: await adminHeaders(app),
    payload: { email, password, name: body.name ?? '' },
  });
  if (created.statusCode !== 201) return created;
  const res = await login(app, email, password, body.client);
  const profile: Record<string, string> = {};
  if (body.uiLanguage) profile.uiLanguage = body.uiLanguage;
  if (body.timezone) profile.timezone = body.timezone;
  if (Object.keys(profile).length > 0) {
    const auth =
      body.client === 'app'
        ? { authorization: `Bearer ${res.json().token}` }
        : { cookie: `wordflow_session=${sessionCookie(res)}` };
    await app.inject({
      method: 'PATCH',
      url: '/api/auth/me',
      headers: { origin: ORIGIN, ...auth },
      payload: profile,
    });
  }
  return res;
}
