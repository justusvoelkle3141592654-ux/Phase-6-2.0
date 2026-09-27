import type { FastifyReply, FastifyRequest } from 'fastify';
import fp from 'fastify-plugin';
import { SESSION_COOKIE } from '@gero/shared';
import type { Db } from '../db';
import type { Session, User } from '../db/schema';
import { resolveSession, SESSION_MS } from './sessions';

declare module 'fastify' {
  interface FastifyRequest {
    user: User | null;
    session: Session | null;
  }
  interface FastifyInstance {
    /** preHandler that answers 401 unless the request is signed in. */
    requireAuth: (request: FastifyRequest, reply: FastifyReply) => Promise<void>;
    setSessionCookie: (request: FastifyRequest, reply: FastifyReply, token: string) => void;
    clearSessionCookie: (request: FastifyRequest, reply: FastifyReply) => void;
  }
}

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function bearerToken(request: FastifyRequest): string | null {
  const header = request.headers.authorization;
  if (!header?.startsWith('Bearer ')) return null;
  return header.slice('Bearer '.length).trim() || null;
}

/** Host with the default ports removed, so "example.org:443" equals "example.org". */
function normalizeHost(host: string): string {
  return host.toLowerCase().replace(/:(80|443)$/, '');
}

function originHost(request: FastifyRequest): string | null | undefined {
  const value = request.headers.origin ?? request.headers.referer;
  if (!value) return undefined;
  try {
    return normalizeHost(new URL(value).host);
  } catch {
    return null;
  }
}

export const authPlugin = fp<{ db: Db; corsOrigins: string[] }>(
  async (app, { db, corsOrigins }) => {
    app.decorateRequest('user', null);
    app.decorateRequest('session', null);

    const cookieOptions = (request: FastifyRequest) => ({
      path: '/',
      httpOnly: true,
      sameSite: 'lax' as const,
      secure: request.protocol === 'https',
    });

    app.decorate(
      'setSessionCookie',
      (request: FastifyRequest, reply: FastifyReply, token: string) => {
        reply.setCookie(SESSION_COOKIE, token, {
          ...cookieOptions(request),
          maxAge: SESSION_MS / 1000,
        });
      },
    );
    app.decorate('clearSessionCookie', (request: FastifyRequest, reply: FastifyReply) => {
      reply.clearCookie(SESSION_COOKIE, cookieOptions(request));
    });

    app.addHook('onRequest', async (request, reply) => {
      const bearer = bearerToken(request);
      const cookie = bearer ? undefined : request.cookies[SESSION_COOKIE];

      // Cross-site protection: requests that change something and do not use a
      // bearer token must come from this host (or from the Android app, which
      // cannot send cookies cross-origin anyway).
      if (!bearer && UNSAFE_METHODS.has(request.method)) {
        const host = originHost(request);
        const allowed =
          host === normalizeHost(request.host) ||
          (host === undefined && !cookie) ||
          (request.headers.origin !== undefined && corsOrigins.includes(request.headers.origin));
        if (!allowed) return reply.code(403).send({ error: 'forbidden_origin' });
      }

      const token = bearer ?? cookie;
      if (!token) return;
      const resolved = resolveSession(db, token);
      if (!resolved) {
        if (cookie) app.clearSessionCookie(request, reply);
        return;
      }
      request.user = resolved.user;
      request.session = resolved.session;
      if (cookie && resolved.extended) app.setSessionCookie(request, reply, cookie);
    });

    app.decorate('requireAuth', async (request: FastifyRequest, reply: FastifyReply) => {
      if (!request.user) return reply.code(401).send({ error: 'unauthorized' });
    });
  },
);
