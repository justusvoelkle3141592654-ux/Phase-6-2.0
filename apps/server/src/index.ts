import fs from 'node:fs';
import path from 'node:path';
import { buildApp } from './app';
import { loadConfig, REPO_ROOT } from './config';

// Optional .env file in the repository root (or ENV_FILE).
const envFile = process.env.ENV_FILE ?? path.join(REPO_ROOT, '.env');
if (fs.existsSync(envFile)) process.loadEnvFile(envFile);

const config = loadConfig();
const app = await buildApp(config);

const shutdown = async (signal: string) => {
  app.log.info(`${signal} empfangen, fahre herunter …`);
  await app.close();
  process.exit(0);
};
process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

try {
  await app.listen({ host: config.host, port: config.port });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
