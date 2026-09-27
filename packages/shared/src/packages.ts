import { z } from 'zod';

/** Language presets; any other language can be entered as free text. */
export const LANGUAGE_PRESETS = ['en', 'fr', 'la'] as const;
export type LanguagePreset = (typeof LANGUAGE_PRESETS)[number];

/** Direction in which cards are asked. Native language is German. */
export const DIRECTIONS = ['foreign_native', 'native_foreign', 'random'] as const;
export type Direction = (typeof DIRECTIONS)[number];
/** Direction of one concrete card. */
export type CardDirection = Exclude<Direction, 'random'>;

export const packageInputSchema = z.object({
  name: z.string().trim().min(1).max(100),
  language: z.string().trim().min(1).max(40),
  direction: z.enum(DIRECTIONS).default('foreign_native'),
});
export type PackageInput = z.input<typeof packageInputSchema>;
export const packageUpdateSchema = packageInputSchema.partial();

export const vocabInputSchema = z.object({
  /** Foreign word (for Latin: the base form that is asked). */
  word: z.string().trim().min(1).max(200),
  /** Optional extra info shown on the back, e.g. stem forms or gender. */
  extra: z.string().trim().max(300).default(''),
  /** German translation; alternatives separated by "," ";" or "/". */
  translation: z.string().trim().min(1).max(300),
});
export type VocabInput = z.input<typeof vocabInputSchema>;
export const vocabUpdateSchema = vocabInputSchema.partial();
export const vocabBatchSchema = z.object({ items: z.array(vocabInputSchema).min(1).max(1000) });

export interface VocabDto {
  id: number;
  packageId: number;
  word: string;
  extra: string;
  translation: string;
  active: boolean;
  /** 1–6 once active. */
  stage: number;
  /** Calendar day (YYYY-MM-DD, account time zone) on which the word is due. */
  dueDate: string | null;
  learned: boolean;
}

export interface PackageCounts {
  total: number;
  inactive: number;
  active: number;
  due: number;
  learned: number;
}

export interface PackageDto {
  id: number;
  name: string;
  language: string;
  direction: Direction;
  createdAt: string;
  counts: PackageCounts;
}

/** Who decided whether an answer was correct. */
export const DECIDERS = ['exact', 'typo', 'ai', 'local', 'correction', 'self'] as const;
export type DecidedBy = (typeof DECIDERS)[number];

export interface LearnCard {
  vocabId: number;
  packageId: number;
  packageName: string;
  language: string;
  packageDirection: Direction;
  word: string;
  extra: string;
  translation: string;
  stage: number;
}

export const answerSchema = z
  .object({
    vocabId: z.int().positive(),
    direction: z.enum(['foreign_native', 'native_foreign']),
    /** Typed answer; null when the card was flipped without typing. */
    answer: z.string().max(500).nullable(),
    /** Self-assessment after flipping (swipe right = true). Only without typed answer. */
    selfGrade: z.boolean().optional(),
    msToFirstKey: z.int().min(0).max(3_600_000).nullable().optional(),
    msTotal: z.int().min(0).max(3_600_000).nullable().optional(),
    /** True for the repetition of a wrong word at the end of the session. */
    repeat: z.boolean().optional(),
  })
  .refine((a) => a.answer !== null || a.selfGrade !== undefined, { path: ['selfGrade'] });
export type AnswerInput = z.input<typeof answerSchema>;

export interface AnswerResult {
  attemptId: number;
  correct: boolean;
  decidedBy: DecidedBy;
  /** Correct spelling when the answer had a small typo. */
  typoOf?: string;
  stageBefore: number;
  stageAfter: number;
  learned: boolean;
  /** "I was right" is offered: no AI verdict, the local check decided. */
  canOverride: boolean;
  /** An AI is set up but gave no verdict in time (or failed). */
  aiFailed?: boolean;
}
