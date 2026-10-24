import fs from 'node:fs';
import path from 'node:path';
import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { eq } from 'drizzle-orm';
import {
  changePasswordSchema,
  deleteAccountSchema,
  loginSchema,
  SUPERUSER_DEFAULT_PASSWORD,
  SUPERUSER_EMAIL,
  updateProfileSchema,
  type AuthResponse,
  type UserDto,
} from '@wordflow/shared';
import type { Db } from '../db';
import { users, type User } from '../db/schema';
import { burnPasswordCheck, hashPassword, verifyPassword } from '../auth/password';
import { createSession, deleteOtherSessions, deleteSession } from '../auth/sessions';
import { parseBody as parse } from './parse';

export function toUserDto(user: User): UserDto {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    role: user.role,
    uiLanguage: user.uiLanguage,
    timezone: user.timezone,
    setupCompleted: user.setupCompleted,
  };
}

/** Rate limit for endpoints that check passwords or codes. */
const limited = { config: { rateLimit: { max: 10, timeWindow: '1 minute' } } };

const DEFAULT_OFFLINE_EMAIL = 'offline@local.test';
const DEFAULT_OFFLINE_PASSWORD = 'offline';

export async function ensureOfflineUser(db: Db): Promise<User> {
  let user = db.select().from(users).where(eq(users.email, DEFAULT_OFFLINE_EMAIL)).get();
  if (!user) {
    user = db
      .insert(users)
      .values({
        email: DEFAULT_OFFLINE_EMAIL,
        passwordHash: await hashPassword(DEFAULT_OFFLINE_PASSWORD),
        uiLanguage: 'de',
        timezone: 'Europe/Berlin',
      })
      .returning()
      .get();
  }
  return user;
}

/**
 * Creates the superuser (admin@wordflow.com / admin) if missing, or promotes an
 * existing account with that address.
 */
export async function ensureSuperuser(db: Db): Promise<User> {
  const existing = db.select().from(users).where(eq(users.email, SUPERUSER_EMAIL)).get();
  if (existing) {
    if (existing.role === 'admin') return existing;
    return db
      .update(users)
      .set({ role: 'admin' })
      .where(eq(users.id, existing.id))
      .returning()
      .get();
  }
  return db
    .insert(users)
    .values({
      email: SUPERUSER_EMAIL,
      name: 'Superuser',
      passwordHash: await hashPassword(SUPERUSER_DEFAULT_PASSWORD),
      role: 'admin',
      uiLanguage: 'de',
      timezone: 'Europe/Berlin',
    })
    .returning()
    .get();
}

/** Removes an account with all its data and photos. */
export async function deleteUser(db: Db, dataDir: string, userId: number) {
  // Foreign keys cascade: sessions, learning progress, settings, attempts.
  db.delete(users).where(eq(users.id, userId)).run();
  await fs.promises.rm(path.join(dataDir, 'uploads', String(userId)), {
    recursive: true,
    force: true,
  });
}

export const authRoutes: FastifyPluginAsync<{
  db: Db;
  offlineMode: boolean;
  dataDir: string;
}> = async (app, { db, offlineMode, dataDir }) => {
  function signIn(
    request: FastifyRequest,
    reply: FastifyReply,
    user: User,
    client: 'web' | 'app',
  ): AuthResponse {
    if (client === 'app') {
      return { user: toUserDto(user), token: createSession(db, user.id, 'token') };
    }
    app.setSessionCookie(request, reply, createSession(db, user.id, 'cookie'));
    return { user: toUserDto(user) };
  }

  // In offline mode the server is a local single-machine setup, so the
  // sign-in screen may list the existing accounts to pick from.
  app.get('/auth/mode', async () => {
    if (!offlineMode) return { offlineMode };
    const accounts = db
      .select({ email: users.email, name: users.name })
      .from(users)
      .orderBy(users.id)
      .all();
    return {
      offlineMode,
      email: DEFAULT_OFFLINE_EMAIL,
      password: DEFAULT_OFFLINE_PASSWORD,
      accounts,
    };
  });

  // Accounts are created by the superuser only.
  app.post('/auth/register', limited, async (_request, reply) => {
    return reply.code(403).send({ error: 'registration_disabled' });
  });

  app.post('/auth/login', limited, async (request, reply) => {
    const input = parse(loginSchema, request.body, reply);
    if (!input) return;
    let user = db.select().from(users).where(eq(users.email, input.email)).get();
    // Recreate the default offline account if it was removed while the server ran.
    if (offlineMode && !user && input.email === DEFAULT_OFFLINE_EMAIL) {
      user = await ensureOfflineUser(db);
    }
    if (!user) {
      await burnPasswordCheck(input.password);
      return reply.code(401).send({ error: 'invalid_credentials' });
    }
    if (!(await verifyPassword(user.passwordHash, input.password))) {
      return reply.code(401).send({ error: 'invalid_credentials' });
    }
    return signIn(request, reply, user, input.client);
  });

  app.post('/auth/logout', async (request, reply) => {
    if (request.session) deleteSession(db, request.session.id);
    app.clearSessionCookie(request, reply);
    return reply.code(204).send();
  });

  app.get('/auth/me', { preHandler: app.requireAuth }, async (request) => {
    return { user: toUserDto(request.user!) };
  });

  app.patch('/auth/me', { preHandler: app.requireAuth }, async (request, reply) => {
    const input = parse(updateProfileSchema, request.body, reply);
    if (!input) return;
    const user =
      Object.keys(input).length === 0
        ? request.user!
        : db.update(users).set(input).where(eq(users.id, request.user!.id)).returning().get();
    return { user: toUserDto(user) };
  });

  app.post(
    '/auth/password',
    { ...limited, preHandler: app.requireAuth },
    async (request, reply) => {
      const input = parse(changePasswordSchema, request.body, reply);
      if (!input) return;
      const user = request.user!;
      if (!(await verifyPassword(user.passwordHash, input.currentPassword))) {
        return reply.code(403).send({ error: 'wrong_password' });
      }
      db.update(users)
        .set({ passwordHash: await hashPassword(input.newPassword) })
        .where(eq(users.id, user.id))
        .run();
      // Sign out everywhere else; the current device stays signed in.
      deleteOtherSessions(db, user.id, request.session!.id);
      return reply.code(204).send();
    },
  );

  // Deletes the account with all its data. The password confirms the intent.
  app.delete('/auth/me', { ...limited, preHandler: app.requireAuth }, async (request, reply) => {
    const input = parse(deleteAccountSchema, request.body, reply);
    if (!input) return;
    const user = request.user!;
    if (user.role === 'admin') return reply.code(403).send({ error: 'forbidden' });
    if (!(await verifyPassword(user.passwordHash, input.password))) {
      return reply.code(403).send({ error: 'wrong_password' });
    }
    await deleteUser(db, dataDir, user.id);
    app.clearSessionCookie(request, reply);
    return reply.code(204).send();
  });
};
