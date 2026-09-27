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

export type User = typeof users.$inferSelect;
export type Session = typeof sessions.$inferSelect;
