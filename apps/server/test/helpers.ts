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
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gero-test-'));
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

/** Extracts the `gero_session` cookie value from a response. */
export function sessionCookie(res: LightMyRequestResponse): string | undefined {
  return res.cookies.find((c) => c.name === 'gero_session')?.value;
}

export async function register(
  app: FastifyInstance,
  body: Record<string, unknown> = {},
): Promise<LightMyRequestResponse> {
  return app.inject({
    method: 'POST',
    url: '/api/auth/register',
    headers: { origin: 'http://localhost:80' },
    payload: {
      email: 'anna@example.org',
      password: 'geheim123',
      registrationCode: TEST_CODE,
      ...body,
    },
  });
}
