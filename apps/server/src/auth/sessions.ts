import crypto from 'node:crypto';
import { and, eq, gt, lt, ne } from 'drizzle-orm';
import { SESSION_DAYS } from '@gero/shared';
import type { Db } from '../db';
import { sessions, users, type Session, type User } from '../db/schema';

const DAY_MS = 24 * 60 * 60 * 1000;
export const SESSION_MS = SESSION_DAYS * DAY_MS;

export function hashToken(token: string): string {
  return crypto.createHash('sha256').update(token).digest('hex');
}

export function createSession(db: Db, userId: number, kind: Session['kind']): string {
  const token = crypto.randomBytes(32).toString('base64url');
  db.insert(sessions)
    .values({
      tokenHash: hashToken(token),
      userId,
      kind,
      expiresAt: new Date(Date.now() + SESSION_MS),
    })
    .run();
  return token;
}

export interface ResolvedSession {
  session: Session;
  user: User;
  /** True when the expiry was pushed forward on this request. */
  extended: boolean;
}

/** Looks up a valid session and slides its expiry (at most once a day). */
export function resolveSession(db: Db, token: string): ResolvedSession | null {
  const now = Date.now();
  const row = db
    .select({ session: sessions, user: users })
    .from(sessions)
    .innerJoin(users, eq(users.id, sessions.userId))
    .where(and(eq(sessions.tokenHash, hashToken(token)), gt(sessions.expiresAt, new Date(now))))
    .get();
  if (!row) return null;

  let extended = false;
  if (row.session.expiresAt.getTime() - now < SESSION_MS - DAY_MS) {
    const expiresAt = new Date(now + SESSION_MS);
    db.update(sessions).set({ expiresAt }).where(eq(sessions.id, row.session.id)).run();
    row.session.expiresAt = expiresAt;
    extended = true;
  }
  return { ...row, extended };
}

export function deleteSession(db: Db, sessionId: number): void {
  db.delete(sessions).where(eq(sessions.id, sessionId)).run();
}

export function deleteOtherSessions(db: Db, userId: number, keepSessionId: number): void {
  db.delete(sessions)
    .where(and(eq(sessions.userId, userId), ne(sessions.id, keepSessionId)))
    .run();
}

export function deleteExpiredSessions(db: Db): void {
  db.delete(sessions).where(lt(sessions.expiresAt, new Date())).run();
}
