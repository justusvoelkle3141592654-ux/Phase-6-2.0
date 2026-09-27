import { sql } from 'drizzle-orm';
import { index, integer, sqliteTable, text } from 'drizzle-orm/sqlite-core';

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

export type User = typeof users.$inferSelect;
export type Session = typeof sessions.$inferSelect;
export type Package = typeof packages.$inferSelect;
export type Vocab = typeof vocab.$inferSelect;
