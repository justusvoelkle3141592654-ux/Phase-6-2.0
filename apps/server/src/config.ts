import path from 'node:path';

/** Repository root, valid for both `apps/server/src` (dev) and `apps/server/dist` (build). */
export const REPO_ROOT = path.resolve(import.meta.dirname, '../../..');

export interface AppConfig {
  host: string;
  port: number;
  /** Directory for the SQLite database, uploaded photos and generated secrets. */
  dataDir: string;
  /** SQLite file; defaults to `<dataDir>/wordflow.db`. Tests use ':memory:'. */
  dbFile?: string;
  /** Directory of the built web app; served as static files when set. */
  webDist: string | null;
  /** Master secret for encrypting API keys. Generated into dataDir when not set. */
  appSecret: string | null;
  /** Code required for self-registration. Generated into dataDir when not set. */
  registrationCode: string | null;
  /** Start in a local single-user mode without the registration gate. */
  offlineMode: boolean;
  /** Set when running behind a reverse proxy (Caddy, nginx) that terminates HTTPS. */
  trustProxy: boolean;
  /** Extra origins allowed for token-authenticated requests (the Android app). */
  corsOrigins: string[];
  logLevel: string;
}

function parseBool(value: string | undefined, fallback: boolean): boolean {
  if (value === undefined || value === '') return fallback;
  return ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function parseList(value: string | undefined): string[] {
  return (value ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

/** Origins used by the Capacitor Android WebView and Vite dev server. */
export const APP_ORIGINS = [
  'http://localhost',
  'https://localhost',
  'http://localhost:5173',
  'http://127.0.0.1:5173',
  'http://localhost:4173',
  'http://127.0.0.1:4173',
  'capacitor://localhost',
];

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const port = Number(env.PORT ?? 3000);
  if (!Number.isInteger(port) || port <= 0 || port > 65535) {
    throw new Error(`Ungültiger PORT: ${env.PORT}`);
  }
  return {
    host: env.HOST ?? '0.0.0.0',
    port,
    dataDir: path.resolve(REPO_ROOT, env.DATA_DIR ?? 'data'),
    webDist: env.WEB_DIST === '' ? null : path.resolve(REPO_ROOT, env.WEB_DIST ?? 'apps/web/dist'),
    appSecret: env.APP_SECRET || null,
    registrationCode: env.REGISTRATION_CODE || null,
    offlineMode: parseBool(env.OFFLINE_MODE, false),
    trustProxy: parseBool(env.TRUST_PROXY, false),
    corsOrigins: [...APP_ORIGINS, ...parseList(env.CORS_ORIGINS)],
    logLevel: env.LOG_LEVEL ?? 'info',
  };
}
