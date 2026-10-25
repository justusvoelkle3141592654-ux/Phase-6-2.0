import { and, eq } from 'drizzle-orm';
import type { Db } from '../db';
import { attempts } from '../db/schema';

/** Words the account has answered today (each word counts once). */
export function seenToday(db: Db, userId: number, today: string): Set<number> {
  const rows = db
    .selectDistinct({ vocabId: attempts.vocabId })
    .from(attempts)
    .where(and(eq(attempts.userId, userId), eq(attempts.day, today)))
    .all();
  return new Set(rows.map((r) => r.vocabId));
}

/**
 * Applies the daily limit to due words: words already asked today may always come
 * again (e.g. after a wrong answer), new ones only while fewer than `limit`
 * different words were asked today.
 */
export function withinDailyLimit<T>(
  due: T[],
  vocabIdOf: (item: T) => number,
  seen: Set<number>,
  limit: number,
): T[] {
  let left = Math.max(0, limit - seen.size);
  return due.filter((item) => {
    if (seen.has(vocabIdOf(item))) return true;
    if (left === 0) return false;
    left--;
    return true;
  });
}
