import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import { afterEach, describe, expect, it } from 'vitest';
import { runMigrations, type Db } from '../src/db';
import * as schema from '../src/db/schema';

const DRIZZLE = path.resolve(import.meta.dirname, '../drizzle');
let tmp: string | null = null;

afterEach(() => {
  if (tmp) fs.rmSync(tmp, { recursive: true, force: true });
  tmp = null;
});

/** A database migrated up to (and including) migration `last`. */
function databaseUpTo(last: number): Db {
  tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-mig-'));
  fs.mkdirSync(path.join(tmp, 'meta'));
  const journal = JSON.parse(fs.readFileSync(path.join(DRIZZLE, 'meta/_journal.json'), 'utf8'));
  journal.entries = journal.entries.filter((e: { idx: number }) => e.idx <= last);
  fs.writeFileSync(path.join(tmp, 'meta/_journal.json'), JSON.stringify(journal));
  for (const e of journal.entries as Array<{ tag: string }>)
    fs.copyFileSync(path.join(DRIZZLE, `${e.tag}.sql`), path.join(tmp, `${e.tag}.sql`));
  const sqlite = new Database(':memory:');
  const db = drizzle({ client: sqlite, schema }) as Db;
  sqlite.pragma('foreign_keys = OFF');
  migrate(db, { migrationsFolder: tmp });
  sqlite.pragma('foreign_keys = ON');
  return db;
}

describe('Migration 0009: eigene Pakete', () => {
  it('kopiert gemeinsame Pakete mit Lernstand für jeden lernenden Account', () => {
    const db = databaseUpTo(8);
    const s = db.$client;
    s.exec(`
      INSERT INTO users (id, email, password_hash, role) VALUES
        (1, 'admin@wordflow.com', 'x', 'admin'),
        (2, 'anna@example.org', 'x', 'user'),
        (3, 'bob@example.org', 'x', 'user');
      INSERT INTO user_settings (user_id, intervals, wrong_mode, ai_timeout_ms, auto_next_correct_ms, auto_next_typo_ms, auto_next_wrong_ms)
        VALUES (2, '[5,10,20,40,80]', 'reset', 2000, 1300, 1300, 0),
               (3, '[5,10,20,40,80]', 'reset', 2000, 4600, 1300, 2000);
      INSERT INTO packages (id, name, language) VALUES (1, 'Lektion 1', 'la'), (2, 'Unit 2', 'en');
      INSERT INTO vocab (id, package_id, word, translation) VALUES
        (1, 1, 'amicus', 'Freund'), (2, 1, 'bellum', 'Krieg'), (3, 2, 'dog', 'Hund');
      INSERT INTO user_vocab (user_id, vocab_id, stage, due_date) VALUES
        (1, 3, 1, '2026-01-01'),
        (2, 1, 3, '2026-02-01');
      INSERT INTO attempts (user_id, vocab_id, direction, correct, decided_by, stage_before, stage_after, day)
        VALUES (2, 1, 'foreign_native', 1, 'exact', 2, 3, '2026-01-20');
      INSERT INTO accepted_answers (vocab_id, direction, answer, verdict, source)
        VALUES (1, 'foreign_native', 'kamerad', 'correct', 'ai');
    `);

    runMigrations(db);

    const pkgs = s.prepare('SELECT id, user_id, name FROM packages ORDER BY id').all();
    expect(pkgs).toEqual([
      { id: 1, user_id: 1, name: 'Lektion 1' },
      { id: 2, user_id: 1, name: 'Unit 2' },
      { id: 3, user_id: 2, name: 'Lektion 1' },
    ]);
    // Anna's copy has all words; her progress and history point to it.
    const words = s
      .prepare('SELECT id, word FROM vocab WHERE package_id = 3 ORDER BY id')
      .all() as Array<{ id: number; word: string }>;
    expect(words.map((w) => w.word)).toEqual(['amicus', 'bellum']);
    const amicus = words[0]!.id;
    expect(s.prepare('SELECT vocab_id, stage FROM user_vocab WHERE user_id = 2').all()).toEqual([
      { vocab_id: amicus, stage: 3 },
    ]);
    expect(s.prepare('SELECT vocab_id FROM attempts WHERE user_id = 2').all()).toEqual([
      { vocab_id: amicus },
    ]);
    expect(s.prepare('SELECT answer FROM accepted_answers WHERE vocab_id = ?').all(amicus)).toEqual(
      [{ answer: 'kamerad' }],
    );
    // The superuser keeps the originals and his own progress.
    expect(s.prepare('SELECT vocab_id FROM user_vocab WHERE user_id = 1').all()).toEqual([
      { vocab_id: 3 },
    ]);

    // New defaults: 3 s instead of the old defaults, whole seconds otherwise; one stage down.
    expect(
      s
        .prepare(
          'SELECT user_id, wrong_mode, auto_next_correct_ms AS c, auto_next_typo_ms AS t, auto_next_wrong_ms AS w, daily_limit AS d FROM user_settings ORDER BY user_id',
        )
        .all(),
    ).toEqual([
      { user_id: 2, wrong_mode: 'back', c: 3000, t: 3000, w: 3000, d: 30 },
      { user_id: 3, wrong_mode: 'back', c: 5000, t: 3000, w: 2000, d: 30 },
    ]);
  });
});
