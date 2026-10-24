import type { FastifyPluginAsync } from 'fastify';
import { and, asc, eq, gt, lte, sql } from 'drizzle-orm';
import {
  addDays,
  todayIn,
  type DueDayDto,
  type OverviewDto,
  type TodayStats,
} from '@wordflow/shared';
import type { Db } from '../db';
import { attempts, userVocab, vocab } from '../db/schema';

function median(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid]! : Math.round((sorted[mid - 1]! + sorted[mid]!) / 2);
}

export function todayStats(db: Db, userId: number, day: string): TodayStats {
  const rows = db
    .select({ attempt: attempts, word: vocab.word, translation: vocab.translation })
    .from(attempts)
    .innerJoin(vocab, eq(vocab.id, attempts.vocabId))
    .where(and(eq(attempts.userId, userId), eq(attempts.day, day)))
    .all();
  const correct = rows.filter((r) => r.attempt.correct).length;
  const wrongByWord = new Map<number, { word: string; translation: string; wrong: number }>();
  for (const r of rows) {
    if (r.attempt.correct) continue;
    const entry = wrongByWord.get(r.attempt.vocabId) ?? {
      word: r.word,
      translation: r.translation,
      wrong: 0,
    };
    entry.wrong++;
    wrongByWord.set(r.attempt.vocabId, entry);
  }
  return {
    attempts: rows.length,
    correct,
    wrong: rows.length - correct,
    accuracy: rows.length ? correct / rows.length : null,
    medianMsToFirstKey: median(
      rows.map((r) => r.attempt.msToFirstKey).filter((v): v is number => v !== null),
    ),
    medianMsTotal: median(
      rows.map((r) => r.attempt.msTotal).filter((v): v is number => v !== null),
    ),
    stageUps: rows.filter((r) => r.attempt.stageAfter > r.attempt.stageBefore).length,
    learnedToday: rows.filter((r) => r.attempt.correct && r.attempt.stageBefore === 6).length,
    difficult: [...wrongByWord.entries()]
      .sort((a, b) => b[1].wrong - a[1].wrong)
      .slice(0, 5)
      .map(([vocabId, v]) => ({ vocabId, ...v })),
  };
}

export const overviewRoutes: FastifyPluginAsync<{ db: Db }> = async (app, { db }) => {
  app.addHook('preHandler', app.requireAuth);

  function overview(userId: number, timezone: string): OverviewDto {
    const today = todayIn(timezone);
    const rows = db
      .select({
        learned: userVocab.learned,
        stage: userVocab.stage,
        due: sql<number>`${userVocab.dueDate} <= ${today}`,
        count: sql<number>`count(*)`,
      })
      .from(userVocab)
      .where(eq(userVocab.userId, userId))
      .groupBy(userVocab.learned, userVocab.stage, sql`${userVocab.dueDate} <= ${today}`)
      .all();
    const total = Number(
      db
        .select({ n: sql<number>`count(*)` })
        .from(vocab)
        .get()?.n ?? 0,
    );
    const stages = [0, 0, 0, 0, 0, 0];
    let due = 0;
    let learned = 0;
    let activated = 0;
    for (const r of rows) {
      const n = Number(r.count);
      activated += n;
      if (r.learned) learned += n;
      else {
        stages[r.stage - 1]! += n;
        if (Number(r.due) === 1) due += n;
      }
    }
    const inactive = Math.max(0, total - activated);
    const next =
      due === 0
        ? db
            .select({ dueDate: userVocab.dueDate })
            .from(userVocab)
            .where(
              and(
                eq(userVocab.userId, userId),
                eq(userVocab.learned, false),
                gt(userVocab.dueDate, today),
              ),
            )
            .orderBy(asc(userVocab.dueDate))
            .limit(1)
            .get()
        : undefined;
    return {
      today,
      due,
      stages,
      learned,
      inactive,
      nextDue: next?.dueDate ?? null,
      todayStats: todayStats(db, userId, today),
    };
  }

  app.get('/overview', async (request) => overview(request.user!.id, request.user!.timezone));

  /**
   * Words due on each of the next 30 days (including overdue ones), for the
   * reminders of the Android app.
   */
  app.get('/overview/due-days', async (request): Promise<{ days: DueDayDto[] }> => {
    const user = request.user!;
    const today = todayIn(user.timezone);
    const last = addDays(today, 29);
    const rows = db
      .select({ dueDate: userVocab.dueDate, count: sql<number>`count(*)` })
      .from(userVocab)
      .where(
        and(
          eq(userVocab.userId, user.id),
          eq(userVocab.learned, false),
          lte(userVocab.dueDate, last),
        ),
      )
      .groupBy(userVocab.dueDate)
      .all();
    const days: DueDayDto[] = [];
    let running = 0;
    const byDay = new Map(rows.map((r) => [r.dueDate!, Number(r.count)]));
    running = rows.filter((r) => r.dueDate! < today).reduce((sum, r) => sum + Number(r.count), 0);
    for (let i = 0; i < 30; i++) {
      const day = addDays(today, i);
      running += byDay.get(day) ?? 0;
      days.push({ day, count: running });
    }
    return { days };
  });
};
