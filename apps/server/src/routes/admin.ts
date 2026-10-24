import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { eq } from 'drizzle-orm';
import Database from 'better-sqlite3';
import AdmZip from 'adm-zip';
import {
  adminCreateUserSchema,
  adminUpdateUserSchema,
  type AdminUserDto,
  type UpdateStatusDto,
} from '@wordflow/shared';
import { runMigrations, type Db } from '../db';
import { sessions, users, type User } from '../db/schema';
import { hashPassword } from '../auth/password';
import { deleteOtherSessions } from '../auth/sessions';
import { deleteUser } from './auth';
import { parseBody } from './parse';

/** GitHub repository the update button installs from. */
export const UPDATE_REPO = 'justusvoelkle3141592654-ux/Phase-6-2.0';
export const UPDATE_BRANCH = 'claude/new-session-uq36ca';

export const MAX_BACKUP_BYTES = 1024 * 1024 * 1024;

export interface AdminOptions {
  db: Db;
  dataDir: string;
  /** Path of the open database file, or ":memory:". */
  dbFile: string;
  repoRoot: string;
  /** Runs after a restore: superuser, photo owners and secrets are brought up to date. */
  afterRestore: () => Promise<void>;
  /** For tests: fetch used to ask GitHub for the latest commit. */
  fetchImpl?: typeof fetch;
}

function toAdminUserDto(u: User): AdminUserDto {
  return {
    id: u.id,
    email: u.email,
    name: u.name,
    role: u.role,
    createdAt: u.createdAt.toISOString(),
  };
}

/** Rejects absolute paths and "..", so a backup cannot write outside the data directory. */
function safeEntryName(name: string): boolean {
  if (name.startsWith('/') || name.includes('\\') || path.isAbsolute(name)) return false;
  return !name.split('/').some((part) => part === '..');
}

function readJson<T>(file: string): T | null {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8')) as T;
  } catch {
    return null;
  }
}

function tail(file: string, lines = 30): string {
  try {
    return fs.readFileSync(file, 'utf8').trimEnd().split('\n').slice(-lines).join('\n');
  } catch {
    return '';
  }
}

function processAlive(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

export const adminRoutes: FastifyPluginAsync<AdminOptions> = async (
  app,
  { db, dataDir, dbFile, repoRoot, afterRestore, fetchImpl = fetch },
) => {
  app.addHook('preHandler', app.requireAdmin);

  function findUser(request: FastifyRequest, reply: FastifyReply): User | null {
    const id = Number((request.params as { id?: string }).id);
    const user = Number.isInteger(id) && db.select().from(users).where(eq(users.id, id)).get();
    if (!user) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return user;
  }

  // ---- Accounts ----

  app.get('/admin/users', async () => {
    const rows = db.select().from(users).orderBy(users.id).all();
    return { users: rows.map(toAdminUserDto) };
  });

  app.post('/admin/users', async (request, reply) => {
    const input = parseBody(adminCreateUserSchema, request.body, reply);
    if (!input) return;
    if (db.select({ id: users.id }).from(users).where(eq(users.email, input.email)).get()) {
      return reply.code(409).send({ error: 'email_taken' });
    }
    const user = db
      .insert(users)
      .values({
        email: input.email,
        name: input.name,
        passwordHash: await hashPassword(input.password),
      })
      .returning()
      .get();
    return reply.code(201).send({ user: toAdminUserDto(user) });
  });

  app.patch('/admin/users/:id', async (request, reply) => {
    const user = findUser(request, reply);
    if (!user) return;
    const input = parseBody(adminUpdateUserSchema, request.body, reply);
    if (!input) return;
    if (input.email && input.email !== user.email) {
      const taken = db
        .select({ id: users.id })
        .from(users)
        .where(eq(users.email, input.email))
        .get();
      if (taken) return reply.code(409).send({ error: 'email_taken' });
    }
    const changes: Partial<User> = {};
    if (input.email !== undefined) changes.email = input.email;
    if (input.name !== undefined) changes.name = input.name;
    if (input.password !== undefined) changes.passwordHash = await hashPassword(input.password);
    const updated =
      Object.keys(changes).length === 0
        ? user
        : db.update(users).set(changes).where(eq(users.id, user.id)).returning().get();
    if (input.password !== undefined) {
      // A new password signs the account out everywhere (except the superuser's own device).
      if (user.id === request.user!.id) deleteOtherSessions(db, user.id, request.session!.id);
      else db.delete(sessions).where(eq(sessions.userId, user.id)).run();
    }
    return { user: toAdminUserDto(updated) };
  });

  app.delete('/admin/users/:id', async (request, reply) => {
    const user = findUser(request, reply);
    if (!user) return;
    if (user.role === 'admin') return reply.code(403).send({ error: 'forbidden' });
    await deleteUser(db, dataDir, user.id);
    return reply.code(204).send();
  });

  // ---- Backup ----

  /** ZIP with the database, all photos and secrets.json (needed for saved API keys). */
  app.get('/admin/backup', async (_request, reply) => {
    const zip = new AdmZip();
    const snapshot = db.$client.serialize();
    // Bytes 18/19 mark WAL mode; the backup is a plain single-file database.
    snapshot[18] = 1;
    snapshot[19] = 1;
    zip.addFile('wordflow.db', snapshot);
    const uploads = path.join(dataDir, 'uploads');
    if (fs.existsSync(uploads)) zip.addLocalFolder(uploads, 'uploads');
    const secrets = path.join(dataDir, 'secrets.json');
    if (fs.existsSync(secrets)) zip.addLocalFile(secrets);
    const day = new Date().toISOString().slice(0, 10);
    return reply
      .type('application/zip')
      .header('Content-Disposition', `attachment; filename="wordflow-backup-${day}.zip"`)
      .header('Cache-Control', 'no-store')
      .send(zip.toBuffer());
  });

  /** Replaces database, photos and secrets with a backup ZIP. Everyone has to sign in again. */
  app.post('/admin/restore', async (request, reply) => {
    if (dbFile === ':memory:') return reply.code(409).send({ error: 'invalid_backup' });
    let buffer: Buffer;
    try {
      const file = await request.file({ limits: { fileSize: MAX_BACKUP_BYTES, files: 1 } });
      if (!file) return reply.code(400).send({ error: 'invalid_backup' });
      buffer = await file.toBuffer();
    } catch (err) {
      const code = (err as { code?: string }).code;
      if (code === 'FST_REQ_FILE_TOO_LARGE')
        return reply.code(413).send({ error: 'invalid_backup' });
      if (code === 'FST_INVALID_MULTIPART_CONTENT_TYPE')
        return reply.code(400).send({ error: 'invalid_backup' });
      throw err;
    }

    let zip: AdmZip;
    try {
      zip = new AdmZip(buffer);
    } catch {
      return reply.code(400).send({ error: 'invalid_backup' });
    }
    const entries = zip.getEntries();
    if (!entries.every((e) => safeEntryName(e.entryName))) {
      return reply.code(400).send({ error: 'invalid_backup' });
    }
    const dbEntry = zip.getEntry('wordflow.db');
    if (!dbEntry) return reply.code(400).send({ error: 'invalid_backup' });

    // The backup must be a WordFlow database.
    const tmp = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'wordflow-restore-'));
    try {
      const tmpDb = path.join(tmp, 'wordflow.db');
      await fs.promises.writeFile(tmpDb, dbEntry.getData());
      let source: Database.Database;
      try {
        source = new Database(tmpDb, { fileMustExist: true });
        source.prepare('select id from users limit 1').all();
      } catch {
        return reply.code(400).send({ error: 'invalid_backup' });
      }
      try {
        // SQLite's backup API copies into the open file; this connection sees the new data.
        await source.backup(dbFile);
      } finally {
        source.close();
      }
    } finally {
      await fs.promises.rm(tmp, { recursive: true, force: true });
    }
    runMigrations(db);

    const uploads = path.join(dataDir, 'uploads');
    await fs.promises.rm(uploads, { recursive: true, force: true });
    for (const entry of entries) {
      if (entry.isDirectory || !entry.entryName.startsWith('uploads/')) continue;
      const target = path.join(dataDir, entry.entryName);
      await fs.promises.mkdir(path.dirname(target), { recursive: true });
      await fs.promises.writeFile(target, entry.getData());
    }
    const secrets = zip.getEntry('secrets.json');
    if (secrets) {
      const file = path.join(dataDir, 'secrets.json');
      await fs.promises.writeFile(file, secrets.getData(), { mode: 0o600 });
      await fs.promises.chmod(file, 0o600);
    }

    await afterRestore();
    app.clearSessionCookie(request, reply);
    return reply.code(204).send();
  });

  // ---- Update from GitHub ----

  const stateFile = path.join(dataDir, 'update.json');
  const lockFile = path.join(dataDir, 'update.lock');
  const logFile = path.join(dataDir, 'update.log');

  function running(): boolean {
    const lock = readJson<{ pid: number }>(lockFile);
    return !!lock && processAlive(lock.pid);
  }

  app.get('/admin/update', async (): Promise<UpdateStatusDto> => {
    const current = readJson<{ sha: string }>(stateFile)?.sha ?? null;
    const status: UpdateStatusDto = {
      current,
      latest: null,
      available: false,
      running: running(),
      log: tail(logFile),
    };
    try {
      const res = await fetchImpl(
        `https://api.github.com/repos/${UPDATE_REPO}/commits/${UPDATE_BRANCH}`,
        {
          headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'WordFlow-Updater' },
          signal: AbortSignal.timeout(10_000),
        },
      );
      if (!res.ok) throw new Error(`GitHub: HTTP ${res.status}`);
      const body = (await res.json()) as { sha?: string };
      status.latest = body.sha ?? null;
      status.available = !!status.latest && status.latest !== current;
    } catch (err) {
      status.error = err instanceof Error ? err.message : 'unknown';
    }
    return status;
  });

  /**
   * Starts scripts/update.mjs as its own process: the dev server reloads while files
   * are being replaced, so the update must not run inside it.
   */
  app.post('/admin/update', async (_request, reply) => {
    if (running()) return reply.code(409).send({ error: 'validation_error' });
    const script = path.join(repoRoot, 'scripts', 'update.mjs');
    if (!fs.existsSync(script)) return reply.code(500).send({ error: 'internal_error' });
    await fs.promises.mkdir(dataDir, { recursive: true });
    const log = fs.openSync(logFile, 'w');
    const child = spawn(
      process.execPath,
      [
        script,
        '--repo',
        UPDATE_REPO,
        '--branch',
        UPDATE_BRANCH,
        '--root',
        repoRoot,
        '--data',
        dataDir,
      ],
      { cwd: repoRoot, detached: true, stdio: ['ignore', log, log], env: process.env },
    );
    fs.closeSync(log);
    fs.writeFileSync(lockFile, JSON.stringify({ pid: child.pid, startedAt: Date.now() }));
    child.unref();
    return reply.code(202).send({ started: true });
  });
};
