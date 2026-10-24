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
  /** Words that can still be learned today (due words within the daily limit). */
  due: number;
  /** All due words, also those beyond the daily limit. */
  dueAll: number;
  /** Words that become due tomorrow. */
  dueTomorrow: number;
  dailyLimit: number;
  /** Active, not learned words per stage 1–6. */
  stages: number[];
  learned: number;
  inactive: number;
  /** Earliest future due date when nothing is due today. */
  nextDue: string | null;
  todayStats: TodayStats;
}
