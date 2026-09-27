import { sql } from 'drizzle-orm';
import { index, integer, sqliteTable, text, uniqueIndex } from 'drizzle-orm/sqlite-core';

const createdAt = () =>
  integer('created_at', { mode: 'timestamp_ms' })
    .notNull()
    .default(sql`(unixepoch('subsec') * 1000)`);

export const users = sqliteTable('users', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  /** Stored lower-case. */
  email: text('email').notNull().unique(),
  passwordHash: text('password_hash').notNull(),
  name: text('name').notNull().default(''),
  uiLanguage: text('ui_language', { enum: ['de', 'en'] })
    .notNull()
    .default('de'),
  /** IANA time zone; decides what "today" means for this account. */
  timezone: text('timezone').notNull().default('Europe/Berlin'),
  setupCompleted: integer('setup_completed', { mode: 'boolean' }).notNull().default(false),
  createdAt: createdAt(),
});

export const sessions = sqliteTable(
  'sessions',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    /** SHA-256 of the session token; the token itself is never stored. */
    tokenHash: text('token_hash').notNull().unique(),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    /** "cookie" for the browser, "token" for the Android app (bearer). */
    kind: text('kind', { enum: ['cookie', 'token'] }).notNull(),
    createdAt: createdAt(),
    expiresAt: integer('expires_at', { mode: 'timestamp_ms' }).notNull(),
  },
  (t) => [index('sessions_user_idx').on(t.userId)],
);

/** Learning and AI preferences, one row per user (created on first access). */
export const userSettings = sqliteTable('user_settings', {
  userId: integer('user_id')
    .primaryKey()
    .references(() => users.id, { onDelete: 'cascade' }),
  intervals: text('intervals', { mode: 'json' }).$type<number[]>().notNull(),
  wrongMode: text('wrong_mode', { enum: ['reset', 'back'] }).notNull(),
  aiTimeoutMs: integer('ai_timeout_ms').notNull(),
  reminderTime: text('reminder_time'),
});

export const packages = sqliteTable(
  'packages',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    name: text('name').notNull(),
    /** Preset code (en, fr, la) or free text. */
    language: text('language').notNull(),
    direction: text('direction', { enum: ['foreign_native', 'native_foreign', 'random'] })
      .notNull()
      .default('foreign_native'),
    createdAt: createdAt(),
  },
  (t) => [index('packages_user_idx').on(t.userId)],
);

export const vocab = sqliteTable(
  'vocab',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    packageId: integer('package_id')
      .notNull()
      .references(() => packages.id, { onDelete: 'cascade' }),
    word: text('word').notNull(),
    extra: text('extra').notNull().default(''),
    translation: text('translation').notNull(),
    active: integer('active', { mode: 'boolean' }).notNull().default(false),
    stage: integer('stage').notNull().default(1),
    /** YYYY-MM-DD in the account's time zone; null while inactive or learned. */
    dueDate: text('due_date'),
    learned: integer('learned', { mode: 'boolean' }).notNull().default(false),
    createdAt: createdAt(),
  },
  (t) => [
    index('vocab_package_idx').on(t.packageId),
    index('vocab_user_due_idx').on(t.userId, t.dueDate),
  ],
);

const CARD_DIRECTIONS = ['foreign_native', 'native_foreign'] as const;

/** Every answer, for the daily summary and statistics. */
export const attempts = sqliteTable(
  'attempts',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    userId: integer('user_id')
      .notNull()
      .references(() => users.id, { onDelete: 'cascade' }),
    vocabId: integer('vocab_id')
      .notNull()
      .references(() => vocab.id, { onDelete: 'cascade' }),
    direction: text('direction', { enum: CARD_DIRECTIONS }).notNull(),
    /** Typed answer; null for self-assessment without typing. */
    answer: text('answer'),
    correct: integer('correct', { mode: 'boolean' }).notNull(),
    decidedBy: text('decided_by', {
      enum: ['exact', 'typo', 'ai', 'local', 'correction', 'self'],
    }).notNull(),
    msToFirstKey: integer('ms_to_first_key'),
    msTotal: integer('ms_total'),
    stageBefore: integer('stage_before').notNull(),
    stageAfter: integer('stage_after').notNull(),
    repeat: integer('repeat', { mode: 'boolean' }).notNull().default(false),
    /** Calendar day in the account's time zone, for "today" statistics. */
    day: text('day').notNull(),
    createdAt: createdAt(),
  },
  (t) => [
    index('attempts_user_day_idx').on(t.userId, t.day),
    index('attempts_vocab_idx').on(t.vocabId),
  ],
);

/** Cached AI verdicts and answers accepted via "I was right", per word and normalized answer. */
export const acceptedAnswers = sqliteTable(
  'accepted_answers',
  {
    id: integer('id').primaryKey({ autoIncrement: true }),
    vocabId: integer('vocab_id')
      .notNull()
      .references(() => vocab.id, { onDelete: 'cascade' }),
    direction: text('direction', { enum: CARD_DIRECTIONS }).notNull(),
    answer: text('answer').notNull(),
    verdict: text('verdict', { enum: ['correct', 'typo', 'wrong'] }).notNull(),
    source: text('source', { enum: ['ai', 'correction'] }).notNull(),
    createdAt: createdAt(),
  },
  (t) => [uniqueIndex('accepted_unique').on(t.vocabId, t.direction, t.answer)],
);

export type User = typeof users.$inferSelect;
export type Session = typeof sessions.$inferSelect;
export type Package = typeof packages.$inferSelect;
export type Vocab = typeof vocab.$inferSelect;
export type Attempt = typeof attempts.$inferSelect;
