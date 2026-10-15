import type { FastifyPluginAsync } from 'fastify';
import { and, asc, eq, gt, lte, sql } from 'drizzle-orm';
import {
  addDays,
  todayIn,
  type DueDayDto,
  type OverviewDto,
  type SummaryDto,
  type TodayStats,
} from '@wordflow/shared';
import type { Db } from '../db';
import { attempts, dailySummaries, vocab } from '../db/schema';
import type { AiService } from '../ai/service';

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

/** Short prompt from aggregated numbers only (no raw answers). */
export function summaryPrompt(stats: TodayStats, due: number, lang: 'de' | 'en'): string {
  const seconds = (ms: number | null) => (ms === null ? '–' : `${(ms / 1000).toFixed(1)} s`);
  const lines = [
    `Abfragen heute: ${stats.attempts} (richtig ${stats.correct}, falsch ${stats.wrong})`,
    `Trefferquote: ${stats.accuracy === null ? '–' : `${Math.round(stats.accuracy * 100)} %`}`,
    `Stufe aufgestiegen: ${stats.stageUps}, heute gelernt (Stufe 6 geschafft): ${stats.learnedToday}`,
    `Zeit bis zum ersten Buchstaben (Median): ${seconds(stats.medianMsToFirstKey)}`,
    `Zeit pro Karte (Median): ${seconds(stats.medianMsTotal)}`,
    `Noch fällig heute: ${due}`,
    `Schwierige Vokabeln: ${
      stats.difficult.length
        ? stats.difficult.map((d) => `${d.word} = ${d.translation} (${d.wrong}× falsch)`).join('; ')
        : 'keine'
    }`,
  ];
  const language = lang === 'de' ? 'Antworte auf Deutsch' : 'Answer in English';
  return (
    `Fasse den Lerntag eines Schülers im Vokabeltrainer zusammen. ${language}, in höchstens fünf kurzen Sätzen, ` +
    'freundlich und konkret: Trefferquote, schwierige Vokabeln, Reaktionsgeschwindigkeit und ein bis zwei kurze Tipps. ' +
    'Kein Markdown, keine Überschriften. Erfinde keine Zahlen.\n\n' +
    lines.join('\n')
  );
}

export const overviewRoutes: FastifyPluginAsync<{ db: Db; ai: AiService }> = async (
  app,
  { db, ai },
) => {
  app.addHook('preHandler', app.requireAuth);

  function overview(userId: number, timezone: string): OverviewDto {
    const today = todayIn(timezone);
    const rows = db
      .select({
        active: vocab.active,
        learned: vocab.learned,
        stage: vocab.stage,
        due: sql<number>`${vocab.dueDate} <= ${today}`,
        count: sql<number>`count(*)`,
      })
      .from(vocab)
      .where(eq(vocab.userId, userId))
      .groupBy(vocab.active, vocab.learned, vocab.stage, sql`${vocab.dueDate} <= ${today}`)
      .all();
    const stages = [0, 0, 0, 0, 0, 0];
    let due = 0;
    let learned = 0;
    let inactive = 0;
    for (const r of rows) {
      const n = Number(r.count);
      if (!r.active) inactive += n;
      else if (r.learned) learned += n;
      else {
        stages[r.stage - 1]! += n;
        if (Number(r.due) === 1) due += n;
      }
    }
    const next =
      due === 0
        ? db
            .select({ dueDate: vocab.dueDate })
            .from(vocab)
            .where(
              and(
                eq(vocab.userId, userId),
                eq(vocab.active, true),
                eq(vocab.learned, false),
                gt(vocab.dueDate, today),
              ),
            )
            .orderBy(asc(vocab.dueDate))
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
      .select({ dueDate: vocab.dueDate, count: sql<number>`count(*)` })
      .from(vocab)
      .where(
        and(
          eq(vocab.userId, user.id),
          eq(vocab.active, true),
          eq(vocab.learned, false),
          lte(vocab.dueDate, last),
        ),
      )
      .groupBy(vocab.dueDate)
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

  /** AI summary of today; cached until new answers come in. */
  app.get('/overview/summary', async (request): Promise<SummaryDto> => {
    const user = request.user!;
    const data = overview(user.id, user.timezone);
    const count = data.todayStats.attempts;
    if (count === 0) return { text: null, reason: 'no_attempts', attempts: 0 };

    const cached = db
      .select()
      .from(dailySummaries)
      .where(and(eq(dailySummaries.userId, user.id), eq(dailySummaries.day, data.today)))
      .get();
    if (cached && cached.attempts === count) return { text: cached.text, attempts: count };

    if (!ai.binding(user.id, 'summary')) {
      return cached
        ? { text: cached.text, attempts: cached.attempts, stale: true, reason: 'no_model' }
        : { text: null, reason: 'no_model', attempts: count };
    }
    try {
      const result = await ai.chat(user.id, 'summary', {
        messages: [
          { role: 'user', content: summaryPrompt(data.todayStats, data.due, user.uiLanguage) },
        ],
        maxTokens: 2000,
        temperature: 0.4,
        signal: AbortSignal.timeout(60_000),
      });
      const text = result.text.trim();
      if (!text) throw new Error('empty');
      const values = { userId: user.id, day: data.today, attempts: count, text };
      db.insert(dailySummaries)
        .values(values)
        .onConflictDoUpdate({
          target: [dailySummaries.userId, dailySummaries.day],
          set: { attempts: count, text },
        })
        .run();
      return { text, attempts: count };
    } catch {
      return cached
        ? { text: cached.text, attempts: cached.attempts, stale: true, reason: 'failed' }
        : { text: null, reason: 'failed', attempts: count };
    }
  });
};
