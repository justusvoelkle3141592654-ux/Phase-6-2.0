import crypto from 'node:crypto';
import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { eq } from 'drizzle-orm';
import {
  changePasswordSchema,
  loginSchema,
  registerSchema,
  updateProfileSchema,
  type AuthResponse,
  type UserDto,
} from '@vokabeltrainer/shared';
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
    uiLanguage: user.uiLanguage,
    timezone: user.timezone,
    setupCompleted: user.setupCompleted,
  };
}

function sameCode(a: string, b: string): boolean {
  const ha = crypto.createHash('sha256').update(a.trim().toUpperCase()).digest();
  const hb = crypto.createHash('sha256').update(b.trim().toUpperCase()).digest();
  return crypto.timingSafeEqual(ha, hb);
}

/** Rate limit for endpoints that check passwords or codes. */
const limited = { config: { rateLimit: { max: 10, timeWindow: '1 minute' } } };

export const authRoutes: FastifyPluginAsync<{ db: Db; registrationCode: string }> = async (
  app,
  { db, registrationCode },
) => {
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

  app.post('/auth/register', limited, async (request, reply) => {
    const input = parse(registerSchema, request.body, reply);
    if (!input) return;
    if (!sameCode(input.registrationCode, registrationCode)) {
      return reply.code(403).send({ error: 'invalid_registration_code' });
    }
    if (db.select({ id: users.id }).from(users).where(eq(users.email, input.email)).get()) {
      return reply.code(409).send({ error: 'email_taken' });
    }
    const user = db
      .insert(users)
      .values({
        email: input.email,
        passwordHash: await hashPassword(input.password),
        uiLanguage: input.uiLanguage,
        timezone: input.timezone,
      })
      .returning()
      .get();
    return reply.code(201).send(signIn(request, reply, user, input.client));
  });

  app.post('/auth/login', limited, async (request, reply) => {
    const input = parse(loginSchema, request.body, reply);
    if (!input) return;
    const user = db.select().from(users).where(eq(users.email, input.email)).get();
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
};
