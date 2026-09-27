export interface DifficultWord {
  vocabId: number;
  word: string;
  translation: string;
  wrong: number;
}

export interface TodayStats {
  attempts: number;
  correct: number;
  wrong: number;
  /** 0–1; null without attempts. */
  accuracy: number | null;
  /** Median time until the first typed letter (typed answers only). */
  medianMsToFirstKey: number | null;
  /** Median time per card. */
  medianMsTotal: number | null;
  stageUps: number;
  learnedToday: number;
  difficult: DifficultWord[];
}

export interface OverviewDto {
  /** Today in the account's time zone (YYYY-MM-DD). */
  today: string;
  due: number;
  /** Active, not learned words per stage 1–6. */
  stages: number[];
  learned: number;
  inactive: number;
  /** Earliest future due date when nothing is due today. */
  nextDue: string | null;
  todayStats: TodayStats;
}

export interface SummaryDto {
  text: string | null;
  /** Why there is no (fresh) text: no attempts today, no model set up, or the AI failed. */
  reason?: 'no_attempts' | 'no_model' | 'failed';
  /** Number of answers the text is based on. */
  attempts: number;
  /** True when the text is older than the latest answers (generation failed). */
  stale?: boolean;
}
