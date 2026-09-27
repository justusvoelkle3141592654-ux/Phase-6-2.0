import { z } from 'zod';

/** Days until a word is due again after reaching stage 2, 3, 4, 5, 6. */
export const DEFAULT_INTERVALS = [5, 10, 20, 40, 80];
export const INTERVAL_MAX_DAYS = 3650;

/** What happens after a wrong answer: back to stage 1, or one stage down. */
export const WRONG_MODES = ['reset', 'back'] as const;
export type WrongMode = (typeof WRONG_MODES)[number];
export const DEFAULT_WRONG_MODE: WrongMode = 'reset';

/** Time limit for the AI answer check before the local decision is used. */
export const DEFAULT_AI_TIMEOUT_MS = 2000;
export const AI_TIMEOUT_MIN_MS = 500;
export const AI_TIMEOUT_MAX_MS = 30000;

/** Preset for answer checking and daily summary (no API key needed). */
export const DEFAULT_AI_PROVIDER = 'Pollinations.ai';
export const DEFAULT_TEXT_MODEL = 'openai/gpt-oss-20b';

export const userSettingsSchema = z.object({
  intervals: z.array(z.int().min(1).max(INTERVAL_MAX_DAYS)).length(5),
  wrongMode: z.enum(WRONG_MODES),
  aiTimeoutMs: z.int().min(AI_TIMEOUT_MIN_MS).max(AI_TIMEOUT_MAX_MS),
  /** Daily reminder in the Android app, "HH:MM" or null for off. */
  reminderTime: z
    .string()
    .regex(/^([01]\d|2[0-3]):[0-5]\d$/)
    .nullable(),
});
export type UserSettings = z.output<typeof userSettingsSchema>;

export const updateSettingsSchema = userSettingsSchema.partial();
export type UpdateSettingsInput = z.input<typeof updateSettingsSchema>;
