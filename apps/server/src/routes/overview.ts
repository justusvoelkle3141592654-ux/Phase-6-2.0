import type { FastifyPluginAsync } from 'fastify';
import { and, asc, eq, gt, lte, sql } from 'drizzle-orm';
import { addDays, todayIn, type OverviewDto, type TodayStats } from '@wordflow/shared';
import type { Db } from '../db';
import { attempts, packages, userVocab, vocab } from '../db/schema';
import { seenToday, withinDailyLimit } from '../learn/limit';
import { getSettings } from './settings';

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
        .innerJoin(packages, eq(packages.id, vocab.packageId))
        .where(eq(packages.userId, userId))
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
    const dueIds = db
      .select({ vocabId: userVocab.vocabId })
      .from(userVocab)
      .where(
        and(
          eq(userVocab.userId, userId),
          eq(userVocab.learned, false),
          lte(userVocab.dueDate, today),
        ),
      )
      .all()
      .map((r) => r.vocabId);
    const dailyLimit = getSettings(db, userId).dailyLimit;
    const available = withinDailyLimit(
      dueIds,
      (id) => id,
      seenToday(db, userId, today),
      dailyLimit,
    ).length;
    const dueTomorrow = Number(
      db
        .select({ n: sql<number>`count(*)` })
        .from(userVocab)
        .where(
          and(
            eq(userVocab.userId, userId),
            eq(userVocab.learned, false),
            eq(userVocab.dueDate, addDays(today, 1)),
          ),
        )
        .get()?.n ?? 0,
    );
    const next =
      available === 0
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
      due: available,
      dueAll: due,
      dueTomorrow,
      dailyLimit,
      stages,
      learned,
      inactive,
      nextDue: next?.dueDate ?? null,
      todayStats: todayStats(db, userId, today),
    };
  }

  app.get('/overview', async (request) => overview(request.user!.id, request.user!.timezone));
};
