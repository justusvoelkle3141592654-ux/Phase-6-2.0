import type { AnswerResult, CardDirection, LearnCard } from '@gero/shared';

/** Direction chosen on the learn page: as set in each package, or one for all cards. */
export type DirectionChoice = 'package' | 'foreign_native' | 'native_foreign' | 'random';

export interface SessionItem {
  card: LearnCard;
  direction: CardDirection;
  /** Second round for a word that was wrong earlier in this session. */
  repeat: boolean;
}

export interface SessionState {
  items: SessionItem[];
  /** Index of the current item; equals items.length when done. */
  index: number;
  choice: DirectionChoice;
  results: Array<{ item: SessionItem; result: AnswerResult }>;
}

export function resolveDirection(
  card: LearnCard,
  choice: DirectionChoice,
  random: () => number = Math.random,
): CardDirection {
  const mode = choice === 'package' ? card.packageDirection : choice;
  if (mode === 'random') return random() < 0.5 ? 'foreign_native' : 'native_foreign';
  return mode;
}

export function startSession(
  cards: LearnCard[],
  choice: DirectionChoice,
  random?: () => number,
): SessionState {
  return {
    items: cards.map((card) => ({
      card,
      direction: resolveDirection(card, choice, random),
      repeat: false,
    })),
    index: 0,
    choice,
    results: [],
  };
}

/** Changes the direction for the cards that have not been shown yet. */
export function changeDirection(
  state: SessionState,
  choice: DirectionChoice,
  random?: () => number,
): SessionState {
  return {
    ...state,
    choice,
    items: state.items.map((item, i) =>
      i <= state.index ? item : { ...item, direction: resolveDirection(item.card, choice, random) },
    ),
  };
}

/**
 * Records the answer to the current card and moves on. A wrong word comes
 * once more at the end of the session (same direction) and counts normally there.
 */
export function recordAnswer(state: SessionState, result: AnswerResult): SessionState {
  const item = state.items[state.index]!;
  const items = [...state.items];
  if (!result.correct && !item.repeat) items.push({ ...item, repeat: true });
  return { ...state, items, index: state.index + 1, results: [...state.results, { item, result }] };
}

/**
 * "I was right" for the last answer: the result becomes correct and the
 * planned repetition is dropped.
 */
export function overrideLast(state: SessionState, result: AnswerResult): SessionState {
  const last = state.results.at(-1);
  if (!last) return state;
  const items = [...state.items];
  if (!last.item.repeat) {
    const repeatIndex = items.findLastIndex(
      (it, i) => i >= state.index && it.repeat && it.card.vocabId === last.item.card.vocabId,
    );
    if (repeatIndex >= 0) items.splice(repeatIndex, 1);
  }
  return { ...state, items, results: [...state.results.slice(0, -1), { item: last.item, result }] };
}

export function sessionStats(state: SessionState) {
  const first = state.results.filter((r) => !r.item.repeat);
  return {
    correct: first.filter((r) => r.result.correct).length,
    wrong: first.filter((r) => !r.result.correct).length,
    repeatedCorrect: state.results.filter((r) => r.item.repeat && r.result.correct).length,
    learned: state.results.filter((r) => r.result.learned).length,
  };
}
