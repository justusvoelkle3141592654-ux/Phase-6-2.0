import { z } from 'zod';

/** Days until a word is due again after reaching stage 2, 3, 4, 5, 6. */
export const DEFAULT_INTERVALS = [5, 10, 20, 40, 80];
export const INTERVAL_MAX_DAYS = 3650;

/** What happens after a wrong answer: back to stage 1, or one stage down. */
export const WRONG_MODES = ['reset', 'back'] as const;
export type WrongMode = (typeof WRONG_MODES)[number];
export const DEFAULT_WRONG_MODE: WrongMode = 'back';

/** Time limit for the AI answer check before the local decision is used. */
export const DEFAULT_AI_TIMEOUT_MS = 2000;
export const AI_TIMEOUT_MIN_MS = 500;
export const AI_TIMEOUT_MAX_MS = 30000;

/**
 * Delay before the next card appears after a typed answer, per outcome, in whole
 * seconds from 0 to 10. 0 means "wait for the Next button".
 */
export const AUTO_NEXT_MAX_MS = 10000;
export const AUTO_NEXT_STEP_MS = 1000;
export const DEFAULT_AUTO_NEXT_CORRECT_MS = 3000;
export const DEFAULT_AUTO_NEXT_TYPO_MS = 3000;
export const DEFAULT_AUTO_NEXT_WRONG_MS = 3000;
const autoNextMs = z.int().min(0).max(AUTO_NEXT_MAX_MS).multipleOf(AUTO_NEXT_STEP_MS);

/** Most different words asked per day (due repetitions and new words together). */
export const DEFAULT_DAILY_LIMIT = 30;
export const DAILY_LIMIT_MAX = 1000;

export const userSettingsSchema = z.object({
  intervals: z.array(z.int().min(1).max(INTERVAL_MAX_DAYS)).length(5),
  wrongMode: z.enum(WRONG_MODES),
  aiTimeoutMs: z.int().min(AI_TIMEOUT_MIN_MS).max(AI_TIMEOUT_MAX_MS),
  /** Correct answer. */
  autoNextCorrectMs: autoNextMs,
  /** Accepted with a typo ("half wrong"). */
  autoNextTypoMs: autoNextMs,
  /** Wrong answer. */
  autoNextWrongMs: autoNextMs,
  dailyLimit: z.int().min(1).max(DAILY_LIMIT_MAX),
});
export type UserSettings = z.output<typeof userSettingsSchema>;

export const updateSettingsSchema = userSettingsSchema.partial();
export type UpdateSettingsInput = z.input<typeof updateSettingsSchema>;
