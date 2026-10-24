import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { and, eq, inArray, sql } from 'drizzle-orm';
import {
  packageInputSchema,
  packageUpdateSchema,
  todayIn,
  vocabBatchSchema,
  vocabUpdateSchema,
  type PackageDto,
  type VocabDto,
} from '@wordflow/shared';
import type { Db } from '../db';
import { packages, userVocab, vocab, type Package, type UserVocab, type Vocab } from '../db/schema';
import { parseBody } from './parse';

/** A word as one account sees it: shared content plus that account's progress. */
export function toVocabDto(v: Vocab, progress?: UserVocab | null): VocabDto {
  return {
    id: v.id,
    packageId: v.packageId,
    word: v.word,
    extra: v.extra,
    translation: v.translation,
    active: !!progress,
    stage: progress?.stage ?? 1,
    dueDate: progress?.dueDate ?? null,
    learned: progress?.learned ?? false,
  };
}

/** Word counts per package from the view of one account. */
function countsFor(db: Db, userId: number, today: string, packageIds: number[]) {
  if (packageIds.length === 0) return new Map<number, PackageDto['counts']>();
  const rows = db
    .select({
      packageId: vocab.packageId,
      total: sql<number>`count(*)`,
      activated: sql<number>`count(${userVocab.vocabId})`,
      learned: sql<number>`sum(case when ${userVocab.learned} = 1 then 1 else 0 end)`,
      due: sql<number>`sum(case when ${userVocab.learned} = 0 and ${userVocab.dueDate} <= ${today} then 1 else 0 end)`,
    })
    .from(vocab)
    .leftJoin(userVocab, and(eq(userVocab.vocabId, vocab.id), eq(userVocab.userId, userId)))
    .where(inArray(vocab.packageId, packageIds))
    .groupBy(vocab.packageId)
    .all();
  const map = new Map<number, PackageDto['counts']>();
  for (const r of rows) {
    const total = Number(r.total);
    const activated = Number(r.activated ?? 0);
    const learned = Number(r.learned ?? 0);
    map.set(r.packageId, {
      total,
      inactive: total - activated,
      active: activated - learned,
      due: Number(r.due ?? 0),
      learned,
    });
  }
  return map;
}

const EMPTY_COUNTS = { total: 0, inactive: 0, active: 0, due: 0, learned: 0 };

function toPackageDto(p: Package, counts: PackageDto['counts'] = EMPTY_COUNTS): PackageDto {
  return {
    id: p.id,
    name: p.name,
    language: p.language,
    direction: p.direction,
    createdAt: p.createdAt.toISOString(),
    counts,
  };
}

function idParam(request: FastifyRequest): number | null {
  const id = Number((request.params as { id?: string }).id);
  return Number.isInteger(id) && id > 0 ? id : null;
}

/**
 * Packages belong to everyone: all accounts may read them and activate words,
 * only the superuser may create, change or delete them.
 */
export const packageRoutes: FastifyPluginAsync<{ db: Db }> = async (app, { db }) => {
  app.addHook('preHandler', app.requireAuth);
  const admin = { preHandler: app.requireAdmin };

  const today = (request: FastifyRequest) => todayIn(request.user!.timezone);

  function findPackage(request: FastifyRequest, reply: FastifyReply): Package | null {
    const id = idParam(request);
    const pkg = id && db.select().from(packages).where(eq(packages.id, id)).get();
    if (!pkg) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return pkg;
  }

  function findVocab(request: FastifyRequest, reply: FastifyReply): Vocab | null {
    const id = idParam(request);
    const row = id && db.select().from(vocab).where(eq(vocab.id, id)).get();
    if (!row) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return row;
  }

  function progressOf(userId: number, vocabId: number) {
    return db
      .select()
      .from(userVocab)
      .where(and(eq(userVocab.userId, userId), eq(userVocab.vocabId, vocabId)))
      .get();
  }

  function packageDto(request: FastifyRequest, pkg: Package): PackageDto {
    return toPackageDto(pkg, countsFor(db, request.user!.id, today(request), [pkg.id]).get(pkg.id));
  }

  app.get('/packages', async (request) => {
    const rows = db.select().from(packages).orderBy(packages.name).all();
    const counts = countsFor(
      db,
      request.user!.id,
      today(request),
      rows.map((p) => p.id),
    );
    return { packages: rows.map((p) => toPackageDto(p, counts.get(p.id))) };
  });

  app.post('/packages', admin, async (request, reply) => {
    const input = parseBody(packageInputSchema, request.body, reply);
    if (!input) return;
    const pkg = db.insert(packages).values(input).returning().get();
    return reply.code(201).send({ package: toPackageDto(pkg) });
  });

  app.get('/packages/:id', async (request, reply) => {
    const pkg = findPackage(request, reply);
    if (!pkg) return;
    const items = db
      .select({ vocab, progress: userVocab })
      .from(vocab)
      .leftJoin(
        userVocab,
        and(eq(userVocab.vocabId, vocab.id), eq(userVocab.userId, request.user!.id)),
      )
      .where(eq(vocab.packageId, pkg.id))
      .orderBy(vocab.id)
      .all();
    return {
      package: packageDto(request, pkg),
      vocab: items.map((r) => toVocabDto(r.vocab, r.progress)),
    };
  });

  app.patch('/packages/:id', admin, async (request, reply) => {
    const pkg = findPackage(request, reply);
    if (!pkg) return;
    const input = parseBody(packageUpdateSchema, request.body, reply);
    if (!input) return;
    const updated =
      Object.keys(input).length === 0
        ? pkg
        : db.update(packages).set(input).where(eq(packages.id, pkg.id)).returning().get();
    return { package: packageDto(request, updated) };
  });

  app.delete('/packages/:id', admin, async (request, reply) => {
    const pkg = findPackage(request, reply);
    if (!pkg) return;
    db.delete(packages).where(eq(packages.id, pkg.id)).run();
    return reply.code(204).send();
  });

  app.post('/packages/:id/vocab', admin, async (request, reply) => {
    const pkg = findPackage(request, reply);
    if (!pkg) return;
    const input = parseBody(vocabBatchSchema, request.body, reply);
    if (!input) return;
    const rows = db
      .insert(vocab)
      .values(input.items.map((item) => ({ ...item, packageId: pkg.id })))
      .returning()
      .all();
    return reply.code(201).send({ vocab: rows.map((v) => toVocabDto(v)) });
  });

  /** Activates all words of a package the account has not activated yet: stage 1, due today. */
  app.post('/packages/:id/activate', async (request, reply) => {
    const pkg = findPackage(request, reply);
    if (!pkg) return;
    const userId = request.user!.id;
    const ids = db
      .select({ id: vocab.id })
      .from(vocab)
      .where(eq(vocab.packageId, pkg.id))
      .all()
      .map((r) => r.id);
    const rows =
      ids.length === 0
        ? []
        : db
            .insert(userVocab)
            .values(ids.map((vocabId) => ({ userId, vocabId, stage: 1, dueDate: today(request) })))
            .onConflictDoNothing()
            .returning({ id: userVocab.vocabId })
            .all();
    return { activated: rows.length, package: packageDto(request, pkg) };
  });

  app.patch('/vocab/:id', admin, async (request, reply) => {
    const row = findVocab(request, reply);
    if (!row) return;
    const input = parseBody(vocabUpdateSchema, request.body, reply);
    if (!input) return;
    const updated =
      Object.keys(input).length === 0
        ? row
        : db.update(vocab).set(input).where(eq(vocab.id, row.id)).returning().get();
    return { vocab: toVocabDto(updated, progressOf(request.user!.id, row.id)) };
  });

  app.delete('/vocab/:id', admin, async (request, reply) => {
    const row = findVocab(request, reply);
    if (!row) return;
    db.delete(vocab).where(eq(vocab.id, row.id)).run();
    return reply.code(204).send();
  });

  app.post('/vocab/:id/activate', async (request, reply) => {
    const row = findVocab(request, reply);
    if (!row) return;
    const userId = request.user!.id;
    db.insert(userVocab)
      .values({ userId, vocabId: row.id, stage: 1, dueDate: today(request) })
      .onConflictDoNothing()
      .run();
    return { vocab: toVocabDto(row, progressOf(userId, row.id)) };
  });

  /** Removes the word from the account's learning plan (progress is lost). */
  app.post('/vocab/:id/deactivate', async (request, reply) => {
    const row = findVocab(request, reply);
    if (!row) return;
    db.delete(userVocab)
      .where(and(eq(userVocab.userId, request.user!.id), eq(userVocab.vocabId, row.id)))
      .run();
    return { vocab: toVocabDto(row) };
  });
};
