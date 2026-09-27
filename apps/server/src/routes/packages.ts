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
} from '@gero/shared';
import type { Db } from '../db';
import { packages, vocab, type Package, type Vocab } from '../db/schema';
import { parseBody } from './parse';

export function toVocabDto(v: Vocab): VocabDto {
  return {
    id: v.id,
    packageId: v.packageId,
    word: v.word,
    extra: v.extra,
    translation: v.translation,
    active: v.active,
    stage: v.stage,
    dueDate: v.dueDate,
    learned: v.learned,
  };
}

function countsFor(db: Db, userId: number, today: string, packageIds: number[]) {
  if (packageIds.length === 0) return new Map<number, PackageDto['counts']>();
  const rows = db
    .select({
      packageId: vocab.packageId,
      total: sql<number>`count(*)`,
      inactive: sql<number>`sum(case when ${vocab.active} = 0 then 1 else 0 end)`,
      learned: sql<number>`sum(case when ${vocab.learned} = 1 then 1 else 0 end)`,
      due: sql<number>`sum(case when ${vocab.active} = 1 and ${vocab.learned} = 0 and ${vocab.dueDate} <= ${today} then 1 else 0 end)`,
    })
    .from(vocab)
    .where(and(eq(vocab.userId, userId), inArray(vocab.packageId, packageIds)))
    .groupBy(vocab.packageId)
    .all();
  const map = new Map<number, PackageDto['counts']>();
  for (const r of rows) {
    const inactive = Number(r.inactive ?? 0);
    const learned = Number(r.learned ?? 0);
    map.set(r.packageId, {
      total: Number(r.total),
      inactive,
      active: Number(r.total) - inactive - learned,
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

export const packageRoutes: FastifyPluginAsync<{ db: Db }> = async (app, { db }) => {
  app.addHook('preHandler', app.requireAuth);

  const today = (request: FastifyRequest) => todayIn(request.user!.timezone);

  /** Loads a package of the signed-in user or answers 404. */
  function ownPackage(request: FastifyRequest, reply: FastifyReply): Package | null {
    const id = idParam(request);
    const pkg =
      id &&
      db
        .select()
        .from(packages)
        .where(and(eq(packages.id, id), eq(packages.userId, request.user!.id)))
        .get();
    if (!pkg) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return pkg;
  }

  function ownVocab(request: FastifyRequest, reply: FastifyReply): Vocab | null {
    const id = idParam(request);
    const row =
      id &&
      db
        .select()
        .from(vocab)
        .where(and(eq(vocab.id, id), eq(vocab.userId, request.user!.id)))
        .get();
    if (!row) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return row;
  }

  function packageDto(request: FastifyRequest, pkg: Package): PackageDto {
    return toPackageDto(pkg, countsFor(db, request.user!.id, today(request), [pkg.id]).get(pkg.id));
  }

  app.get('/packages', async (request) => {
    const rows = db
      .select()
      .from(packages)
      .where(eq(packages.userId, request.user!.id))
      .orderBy(packages.name)
      .all();
    const counts = countsFor(
      db,
      request.user!.id,
      today(request),
      rows.map((p) => p.id),
    );
    return { packages: rows.map((p) => toPackageDto(p, counts.get(p.id))) };
  });

  app.post('/packages', async (request, reply) => {
    const input = parseBody(packageInputSchema, request.body, reply);
    if (!input) return;
    const pkg = db
      .insert(packages)
      .values({ ...input, userId: request.user!.id })
      .returning()
      .get();
    return reply.code(201).send({ package: toPackageDto(pkg) });
  });

  app.get('/packages/:id', async (request, reply) => {
    const pkg = ownPackage(request, reply);
    if (!pkg) return;
    const items = db
      .select()
      .from(vocab)
      .where(eq(vocab.packageId, pkg.id))
      .orderBy(vocab.id)
      .all();
    return { package: packageDto(request, pkg), vocab: items.map(toVocabDto) };
  });

  app.patch('/packages/:id', async (request, reply) => {
    const pkg = ownPackage(request, reply);
    if (!pkg) return;
    const input = parseBody(packageUpdateSchema, request.body, reply);
    if (!input) return;
    const updated =
      Object.keys(input).length === 0
        ? pkg
        : db.update(packages).set(input).where(eq(packages.id, pkg.id)).returning().get();
    return { package: packageDto(request, updated) };
  });

  app.delete('/packages/:id', async (request, reply) => {
    const pkg = ownPackage(request, reply);
    if (!pkg) return;
    db.delete(packages).where(eq(packages.id, pkg.id)).run();
    return reply.code(204).send();
  });

  app.post('/packages/:id/vocab', async (request, reply) => {
    const pkg = ownPackage(request, reply);
    if (!pkg) return;
    const input = parseBody(vocabBatchSchema, request.body, reply);
    if (!input) return;
    const rows = db
      .insert(vocab)
      .values(input.items.map((item) => ({ ...item, userId: request.user!.id, packageId: pkg.id })))
      .returning()
      .all();
    return reply.code(201).send({ vocab: rows.map(toVocabDto) });
  });

  /** Activates all inactive words of a package: stage 1, due today. */
  app.post('/packages/:id/activate', async (request, reply) => {
    const pkg = ownPackage(request, reply);
    if (!pkg) return;
    const rows = db
      .update(vocab)
      .set({ active: true, stage: 1, dueDate: today(request) })
      .where(and(eq(vocab.packageId, pkg.id), eq(vocab.active, false)))
      .returning({ id: vocab.id })
      .all();
    return { activated: rows.length, package: packageDto(request, pkg) };
  });

  app.patch('/vocab/:id', async (request, reply) => {
    const row = ownVocab(request, reply);
    if (!row) return;
    const input = parseBody(vocabUpdateSchema, request.body, reply);
    if (!input) return;
    const updated =
      Object.keys(input).length === 0
        ? row
        : db.update(vocab).set(input).where(eq(vocab.id, row.id)).returning().get();
    return { vocab: toVocabDto(updated) };
  });

  app.delete('/vocab/:id', async (request, reply) => {
    const row = ownVocab(request, reply);
    if (!row) return;
    db.delete(vocab).where(eq(vocab.id, row.id)).run();
    return reply.code(204).send();
  });

  app.post('/vocab/:id/activate', async (request, reply) => {
    const row = ownVocab(request, reply);
    if (!row) return;
    if (row.active) return { vocab: toVocabDto(row) };
    const updated = db
      .update(vocab)
      .set({ active: true, stage: 1, dueDate: today(request) })
      .where(eq(vocab.id, row.id))
      .returning()
      .get();
    return { vocab: toVocabDto(updated) };
  });
};
