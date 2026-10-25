import type { FastifyPluginAsync } from 'fastify';
import { eq } from 'drizzle-orm';
import {
  DEFAULT_AI_TIMEOUT_MS,
  DEFAULT_AUTO_NEXT_CORRECT_MS,
  DEFAULT_AUTO_NEXT_TYPO_MS,
  DEFAULT_AUTO_NEXT_WRONG_MS,
  DEFAULT_DAILY_LIMIT,
  DEFAULT_INTERVALS,
  DEFAULT_WRONG_MODE,
  updateSettingsSchema,
  type UserSettings,
} from '@wordflow/shared';
import type { Db } from '../db';
import { userSettings } from '../db/schema';
import { parseBody } from './parse';
import { toUserDto } from './auth';

export function getSettings(db: Db, userId: number): UserSettings {
  const row =
    db.select().from(userSettings).where(eq(userSettings.userId, userId)).get() ??
    db
      .insert(userSettings)
      .values({
        userId,
        intervals: DEFAULT_INTERVALS,
        wrongMode: DEFAULT_WRONG_MODE,
        aiTimeoutMs: DEFAULT_AI_TIMEOUT_MS,
        autoNextCorrectMs: DEFAULT_AUTO_NEXT_CORRECT_MS,
        autoNextTypoMs: DEFAULT_AUTO_NEXT_TYPO_MS,
        autoNextWrongMs: DEFAULT_AUTO_NEXT_WRONG_MS,
        dailyLimit: DEFAULT_DAILY_LIMIT,
      })
      .returning()
      .get();
  const { userId: _, ...settings } = row;
  return settings;
}

export const settingsRoutes: FastifyPluginAsync<{ db: Db }> = async (app, { db }) => {
  app.addHook('preHandler', app.requireAuth);

  app.get('/settings', async (request) => ({ settings: getSettings(db, request.user!.id) }));

  // Older web and app versions finish their setup wizard here; nothing to store any more.
  app.post('/setup/complete', async (request) => ({ user: toUserDto(request.user!) }));

  app.patch('/settings', async (request, reply) => {
    const input = parseBody(updateSettingsSchema, request.body, reply);
    if (!input) return;
    const userId = request.user!.id;
    getSettings(db, userId);
    if (Object.keys(input).length > 0) {
      db.update(userSettings).set(input).where(eq(userSettings.userId, userId)).run();
    }
    return { settings: getSettings(db, userId) };
  });
};
