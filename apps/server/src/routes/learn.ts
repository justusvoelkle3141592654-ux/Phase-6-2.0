import type { FastifyPluginAsync } from 'fastify';
import { and, desc, eq, lte } from 'drizzle-orm';
import {
  answerSchema,
  applyAnswer,
  todayIn,
  type AnswerResult,
  type LearnCard,
} from '@vokabeltrainer/shared';
import type { Db } from '../db';
import { attempts, packages, vocab } from '../db/schema';
import { decide, rememberCorrection, type AiCheck, type Decision } from '../learn/decide';
import { getSettings } from './settings';
import { parseBody } from './parse';

export interface LearnOptions {
  db: Db;
  /** Returns the AI check for a user and package language, or undefined without AI. */
  aiCheckFor?: (userId: number, language: string) => AiCheck | undefined;
  /** Loads the checking model so the first answer is fast. */
  warmUp?: (userId: number) => Promise<void>;
}

function shuffle<T>(items: T[]): T[] {
  for (let i = items.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [items[i], items[j]] = [items[j]!, items[i]!];
  }
  return items;
}

export const learnRoutes: FastifyPluginAsync<LearnOptions> = async (
  app,
  { db, aiCheckFor, warmUp },
) => {
  app.addHook('preHandler', app.requireAuth);

  /** Due cards (optionally of one package), in random order. */
  app.get('/learn/cards', async (request) => {
    const user = request.user!;
    const packageId = Number((request.query as { packageId?: string }).packageId) || null;
    const rows = db
      .select({ vocab, pkg: packages })
      .from(vocab)
      .innerJoin(packages, eq(packages.id, vocab.packageId))
      .where(
        and(
          eq(vocab.userId, user.id),
          eq(vocab.active, true),
          eq(vocab.learned, false),
          lte(vocab.dueDate, todayIn(user.timezone)),
          packageId ? eq(vocab.packageId, packageId) : undefined,
        ),
      )
      .all();
    const cards: LearnCard[] = rows.map(({ vocab: v, pkg }) => ({
      vocabId: v.id,
      packageId: pkg.id,
      packageName: pkg.name,
      language: pkg.language,
      packageDirection: pkg.direction,
      word: v.word,
      extra: v.extra,
      translation: v.translation,
      stage: v.stage,
    }));
    return { cards: shuffle(cards) };
  });

  app.post('/learn/answer', async (request, reply) => {
    const input = parseBody(answerSchema, request.body, reply);
    if (!input) return;
    const user = request.user!;
    const word = db
      .select()
      .from(vocab)
      .where(and(eq(vocab.id, input.vocabId), eq(vocab.userId, user.id)))
      .get();
    if (!word || !word.active || word.learned) return reply.code(404).send({ error: 'not_found' });

    const language = db
      .select({ language: packages.language })
      .from(packages)
      .where(eq(packages.id, word.packageId))
      .get()!.language;
    const decision: Decision =
      input.answer === null
        ? { correct: input.selfGrade!, decidedBy: 'self', canOverride: false }
        : await decide(db, word, input.direction, input.answer, aiCheckFor?.(user.id, language));

    const today = todayIn(user.timezone);
    const next = applyAnswer(word.stage, decision.correct, getSettings(db, user.id), today);
    const attempt = db.transaction((tx) => {
      tx.update(vocab).set(next).where(eq(vocab.id, word.id)).run();
      return tx
        .insert(attempts)
        .values({
          userId: user.id,
          vocabId: word.id,
          direction: input.direction,
          answer: input.answer,
          correct: decision.correct,
          decidedBy: decision.decidedBy,
          msToFirstKey: input.msToFirstKey ?? null,
          msTotal: input.msTotal ?? null,
          stageBefore: word.stage,
          stageAfter: next.stage,
          repeat: input.repeat ?? false,
          day: today,
        })
        .returning()
        .get();
    });

    const result: AnswerResult = {
      attemptId: attempt.id,
      correct: decision.correct,
      decidedBy: decision.decidedBy,
      typoOf: decision.typoOf,
      aiFailed: decision.aiFailed,
      stageBefore: word.stage,
      stageAfter: next.stage,
      learned: next.learned,
      canOverride: decision.canOverride,
    };
    return result;
  });

  /** Called when a session starts; answers right away, loading happens in the background. */
  app.post('/learn/warmup', async (request, reply) => {
    void warmUp?.(request.user!.id).catch(() => undefined);
    return reply.code(204).send();
  });

  /** "I was right": only for the latest answer of a word that the local check marked wrong. */
  app.post('/learn/attempts/:id/override', async (request, reply) => {
    const user = request.user!;
    const id = Number((request.params as { id: string }).id);
    const attempt = db
      .select()
      .from(attempts)
      .where(and(eq(attempts.id, id), eq(attempts.userId, user.id)))
      .get();
    if (!attempt) return reply.code(404).send({ error: 'not_found' });
    const latest = db
      .select({ id: attempts.id })
      .from(attempts)
      .where(eq(attempts.vocabId, attempt.vocabId))
      .orderBy(desc(attempts.id))
      .limit(1)
      .get();
    const allowed =
      attempt.decidedBy === 'local' &&
      !attempt.correct &&
      !!attempt.answer?.trim() &&
      latest?.id === attempt.id;
    if (!allowed) return reply.code(409).send({ error: 'validation_error' });

    const next = applyAnswer(
      attempt.stageBefore,
      true,
      getSettings(db, user.id),
      todayIn(user.timezone),
    );
    db.transaction((tx) => {
      tx.update(vocab).set(next).where(eq(vocab.id, attempt.vocabId)).run();
      tx.update(attempts)
        .set({ correct: true, decidedBy: 'correction', stageAfter: next.stage })
        .where(eq(attempts.id, attempt.id))
        .run();
    });
    rememberCorrection(db, attempt.vocabId, attempt.direction, attempt.answer!);

    const result: AnswerResult = {
      attemptId: attempt.id,
      correct: true,
      decidedBy: 'correction',
      stageBefore: attempt.stageBefore,
      stageAfter: next.stage,
      learned: next.learned,
      canOverride: false,
    };
    return result;
  });
};
