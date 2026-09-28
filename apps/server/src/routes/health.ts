import type { FastifyPluginAsync } from 'fastify';
import { APP_NAME, type HealthResponse } from '@vokabeltrainer/shared';
import { APP_VERSION } from '../version';

export const healthRoutes: FastifyPluginAsync = async (app) => {
  app.get('/health', async (): Promise<HealthResponse> => {
    return { status: 'ok', name: APP_NAME, version: APP_VERSION };
  });
};
