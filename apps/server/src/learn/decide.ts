import { and, eq } from 'drizzle-orm';
import { checkLocally, normalize, type CardDirection, type DecidedBy } from '@gero/shared';
import type { Db } from '../db';
import { acceptedAnswers, type Vocab } from '../db/schema';

export interface Decision {
  correct: boolean;
  decidedBy: DecidedBy;
  typoOf?: string;
  canOverride: boolean;
}

/** Answer check by an AI model; resolves to null when no verdict is available in time. */
export type AiCheck = (input: {
  vocab: Vocab;
  direction: CardDirection;
  answer: string;
  solution: string;
}) => Promise<'correct' | 'typo' | 'wrong' | null>;

export function promptAndSolution(vocab: Vocab, direction: CardDirection) {
  return direction === 'foreign_native'
    ? { prompt: vocab.word, solution: vocab.translation }
    : { prompt: vocab.translation, solution: vocab.word };
}

/**
 * Answer pipeline: empty → wrong; exact or small typo → correct; cached AI
 * verdict or accepted correction; AI; otherwise the local decision (wrong)
 * with the option "I was right".
 */
export async function decide(
  db: Db,
  vocab: Vocab,
  direction: CardDirection,
  answer: string,
  aiCheck?: AiCheck,
): Promise<Decision> {
  const { solution } = promptAndSolution(vocab, direction);
  const local = checkLocally(answer, solution);
  if (local.verdict === 'empty') return { correct: false, decidedBy: 'local', canOverride: false };
  if (local.verdict === 'exact') return { correct: true, decidedBy: 'exact', canOverride: false };
  if (local.verdict === 'typo') {
    return { correct: true, decidedBy: 'typo', typoOf: local.matched, canOverride: false };
  }

  const normalized = normalize(answer);
  const cached = db
    .select()
    .from(acceptedAnswers)
    .where(
      and(
        eq(acceptedAnswers.vocabId, vocab.id),
        eq(acceptedAnswers.direction, direction),
        eq(acceptedAnswers.answer, normalized),
      ),
    )
    .get();
  if (cached) {
    return {
      correct: cached.verdict !== 'wrong',
      decidedBy: cached.source === 'correction' ? 'correction' : 'ai',
      typoOf: cached.verdict === 'typo' ? solution : undefined,
      canOverride: false,
    };
  }

  const verdict = aiCheck ? await aiCheck({ vocab, direction, answer, solution }) : null;
  if (verdict) {
    db.insert(acceptedAnswers)
      .values({ vocabId: vocab.id, direction, answer: normalized, verdict, source: 'ai' })
      .onConflictDoNothing()
      .run();
    return {
      correct: verdict !== 'wrong',
      decidedBy: 'ai',
      typoOf: verdict === 'typo' ? solution : undefined,
      canOverride: false,
    };
  }
  return { correct: false, decidedBy: 'local', canOverride: true };
}

/** Remembers an answer the user marked as correct ("I was right"). */
export function rememberCorrection(
  db: Db,
  vocabId: number,
  direction: CardDirection,
  answer: string,
) {
  db.insert(acceptedAnswers)
    .values({
      vocabId,
      direction,
      answer: normalize(answer),
      verdict: 'correct',
      source: 'correction',
    })
    .onConflictDoUpdate({
      target: [acceptedAnswers.vocabId, acceptedAnswers.direction, acceptedAnswers.answer],
      set: { verdict: 'correct', source: 'correction' },
    })
    .run();
}
