import { addDays } from './dates';
import { STAGE_COUNT } from './constants';
import type { WrongMode } from './settings';

export interface StageState {
  stage: number;
  dueDate: string | null;
  learned: boolean;
}

/**
 * Applies one answer to a word.
 * - Correct: one stage up, due after the interval of the new stage.
 *   Correct on stage 6: learned, never asked again.
 * - Wrong: back to stage 1, or one stage down (setting). The word is due
 *   again right away.
 */
export function applyAnswer(
  stage: number,
  correct: boolean,
  settings: { intervals: number[]; wrongMode: WrongMode },
  today: string,
): StageState {
  if (correct) {
    if (stage >= STAGE_COUNT) return { stage: STAGE_COUNT, dueDate: null, learned: true };
    const next = stage + 1;
    return { stage: next, dueDate: addDays(today, settings.intervals[next - 2]!), learned: false };
  }
  const next = settings.wrongMode === 'reset' ? 1 : Math.max(1, stage - 1);
  return { stage: next, dueDate: today, learned: false };
}
