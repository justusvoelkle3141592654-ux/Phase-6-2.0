import fs from 'node:fs';
import path from 'node:path';
import Database from 'better-sqlite3';
import { drizzle, type BetterSQLite3Database } from 'drizzle-orm/better-sqlite3';
import { migrate } from 'drizzle-orm/better-sqlite3/migrator';
import * as schema from './schema';

export type Db = BetterSQLite3Database<typeof schema> & { $client: Database.Database };

/** Migrations live in apps/server/drizzle (dev) or dist/drizzle (build). */
function migrationsFolder(): string {
  const candidates = [
    path.resolve(import.meta.dirname, 'drizzle'),
    path.resolve(import.meta.dirname, '../drizzle'),
    path.resolve(import.meta.dirname, '../../drizzle'),
  ];
  const found = candidates.find((dir) => fs.existsSync(path.join(dir, 'meta', '_journal.json')));
  if (!found) throw new Error('Datenbank-Migrationen nicht gefunden');
  return found;
}

export function openDatabase(file: string): Db {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const sqlite = new Database(file);
  sqlite.pragma('journal_mode = WAL');
  sqlite.pragma('foreign_keys = ON');
  sqlite.pragma('busy_timeout = 5000');
  const db = drizzle({ client: sqlite, schema });
  migrate(db, { migrationsFolder: migrationsFolder() });
  return db;
}
