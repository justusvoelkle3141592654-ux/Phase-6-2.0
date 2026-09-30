import fs from 'node:fs';
import path from 'node:path';
import Fastify, { type FastifyInstance } from 'fastify';
import fastifyCookie from '@fastify/cookie';
import fastifyCors from '@fastify/cors';
import fastifyMultipart from '@fastify/multipart';
import fastifyRateLimit from '@fastify/rate-limit';
import fastifyStatic from '@fastify/static';
import type { AppConfig } from './config';
import { openDatabase } from './db';
import { loadSecrets } from './secrets';
import { authPlugin } from './auth/plugin';
import { deleteExpiredSessions } from './auth/sessions';
import { authRoutes, ensureOfflineUser } from './routes/auth';
import { healthRoutes } from './routes/health';
import { settingsRoutes } from './routes/settings';
import { packageRoutes } from './routes/packages';
import { learnRoutes } from './routes/learn';
import { aiRoutes } from './routes/ai';
import { overviewRoutes } from './routes/overview';
import { MAX_PHOTO_BYTES, MAX_PHOTOS, uploadRoutes } from './routes/uploads';
import { AiService } from './ai/service';
import { aiCheckFor } from './learn/ai-check';

export async function buildApp(config: AppConfig): Promise<FastifyInstance> {
  const app = Fastify({
    logger: config.logLevel === 'silent' ? false : { level: config.logLevel },
    trustProxy: config.trustProxy,
    bodyLimit: 1024 * 1024,
  });

  const secrets = loadSecrets(config.dataDir, {
    registrationCode: config.registrationCode,
    appSecret: config.appSecret,
  });
  if (secrets.generated.length > 0) {
    app.log.info(
      `Neue Schlüssel erzeugt und in ${path.join(config.dataDir, 'secrets.json')} gespeichert.`,
    );
  }
  if (!config.registrationCode && !config.offlineMode) {
    app.log.info(`Registrierungscode: ${secrets.registrationCode}`);
  }
  if (config.offlineMode) {
    app.log.info('Offline-Modus aktiv: Ein Standard-Account wird ohne Registrierung gestartet.');
  }

  const db = openDatabase(config.dbFile ?? path.join(config.dataDir, 'wordflow.db'));
  deleteExpiredSessions(db);
  if (config.offlineMode) {
    await ensureOfflineUser(db);
  }
  const ai = new AiService(db, secrets.appSecret);
  app.addHook('onClose', async () => db.$client.close());

  app.setErrorHandler((error: { statusCode?: number }, request, reply) => {
    const status = error.statusCode ?? 500;
    if (status >= 500) {
      request.log.error(error);
      return reply.code(500).send({ error: 'internal_error' });
    }
    if (status === 429) return reply.code(429).send({ error: 'rate_limited' });
    return reply.code(status).send({ error: 'validation_error' });
  });

  await app.register(fastifyCookie);
  // The Android app calls the API from its own origin with a bearer token, never with cookies.
  await app.register(fastifyCors, {
    origin: config.corsOrigins,
    credentials: false,
    methods: ['GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  });
  await app.register(fastifyRateLimit, {
    global: false,
    errorResponseBuilder: (_request, context) => ({ statusCode: context.statusCode }),
  });
  await app.register(fastifyMultipart, {
    limits: { fileSize: MAX_PHOTO_BYTES, files: MAX_PHOTOS },
  });
  await app.register(authPlugin, { db, corsOrigins: config.corsOrigins });

  await app.register(
    async (api) => {
      await api.register(healthRoutes);
      await api.register(authRoutes, {
        db,
        registrationCode: secrets.registrationCode,
        offlineMode: config.offlineMode,
      });
      await api.register(settingsRoutes, { db });
      await api.register(packageRoutes, { db });
      await api.register(learnRoutes, {
        db,
        aiCheckFor: (userId, language) => aiCheckFor(db, ai, userId, language),
        warmUp: async (userId) => {
          const binding = ai.binding(userId, 'check');
          await binding?.provider.warmUp(binding.model, AbortSignal.timeout(15_000));
        },
      });
      await api.register(aiRoutes, { db, ai });
      await api.register(overviewRoutes, { db, ai });
      await api.register(uploadRoutes, { db, ai, dataDir: config.dataDir });
    },
    { prefix: '/api' },
  );

  const indexHtml = config.webDist ? path.join(config.webDist, 'index.html') : null;
  if (config.webDist && indexHtml && fs.existsSync(indexHtml)) {
    await app.register(fastifyStatic, { root: config.webDist, wildcard: false });
    // Single-page app: unknown non-API GET routes deliver index.html.
    app.setNotFoundHandler((request, reply) => {
      if (request.method === 'GET' && !request.url.startsWith('/api/')) {
        return reply.type('text/html').sendFile('index.html');
      }
      return reply.code(404).send({ error: 'not_found' });
    });
  } else {
    app.setNotFoundHandler((_request, reply) => reply.code(404).send({ error: 'not_found' }));
  }

  return app;
}
