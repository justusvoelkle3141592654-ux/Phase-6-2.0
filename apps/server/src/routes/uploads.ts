import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { and, desc, eq, inArray, isNull } from 'drizzle-orm';
import {
  saveUploadSchema,
  type PackagePhotoDto,
  type UploadJobDto,
  type UploadPageDto,
} from '@wordflow/shared';
import type { Db } from '../db';
import { packages, uploadJobs, uploadPages, vocab, type UploadPage } from '../db/schema';
import type { AiService } from '../ai/service';
import { recognize, RecognitionError } from '../uploads/recognize';
import { parseBody } from './parse';

const MEDIA_TYPES: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
};
export const MAX_PHOTOS = 20;
export const MAX_PHOTO_BYTES = 15 * 1024 * 1024;

function pageDto(p: UploadPage): UploadPageDto {
  return {
    id: p.id,
    status: p.status,
    error: p.error,
    errorMessage: p.errorMessage,
    entries: p.entries,
    photoUrl: `/api/uploads/pages/${p.id}/photo`,
  };
}

export interface UploadOptions {
  db: Db;
  ai: AiService;
  dataDir: string;
}

export const uploadRoutes: FastifyPluginAsync<UploadOptions> = async (app, { db, ai, dataDir }) => {
  const dirFor = (userId: number) => path.join(dataDir, 'uploads', String(userId));

  // Recognition that was running when the server stopped cannot continue.
  db.update(uploadPages)
    .set({ status: 'failed', error: 'interrupted', errorMessage: null })
    .where(inArray(uploadPages.status, ['pending', 'processing']))
    .run();

  /** Recognises the pages of a user one after another, in the background. */
  const queues = new Map<number, Promise<void>>();
  function enqueue(userId: number, pageIds: number[]) {
    const previous = queues.get(userId) ?? Promise.resolve();
    const next = previous.then(async () => {
      for (const id of pageIds) await process(userId, id);
    });
    queues.set(
      userId,
      next.catch(() => undefined),
    );
  }

  async function process(userId: number, pageId: number) {
    const page = db.select().from(uploadPages).where(eq(uploadPages.id, pageId)).get();
    if (!page) return;
    db.update(uploadPages).set({ status: 'processing' }).where(eq(uploadPages.id, pageId)).run();
    try {
      const base64 = (
        await fs.promises.readFile(path.join(dirFor(userId), page.fileName))
      ).toString('base64');
      const entries = await recognize(ai, userId, { mediaType: page.mediaType, base64 });
      db.update(uploadPages)
        .set({ status: 'done', entries, error: null, errorMessage: null })
        .where(eq(uploadPages.id, pageId))
        .run();
    } catch (err) {
      const code = err instanceof RecognitionError ? err.code : 'internal_error';
      const message = err instanceof Error ? err.message.slice(0, 300) : null;
      if (!(err instanceof RecognitionError)) app.log.error(err);
      db.update(uploadPages)
        .set({ status: 'failed', error: code, errorMessage: message })
        .where(eq(uploadPages.id, pageId))
        .run();
    }
  }

  function jobDto(jobId: number): UploadJobDto {
    const job = db.select().from(uploadJobs).where(eq(uploadJobs.id, jobId)).get()!;
    const pages = db
      .select()
      .from(uploadPages)
      .where(eq(uploadPages.jobId, jobId))
      .orderBy(uploadPages.id)
      .all();
    return {
      id: job.id,
      createdAt: job.createdAt.toISOString(),
      packageId: job.packageId,
      pages: pages.map(pageDto),
    };
  }

  function ownJob(request: FastifyRequest, reply: FastifyReply) {
    const id = Number((request.params as { id: string }).id);
    const job =
      Number.isInteger(id) &&
      db
        .select()
        .from(uploadJobs)
        .where(and(eq(uploadJobs.id, id), eq(uploadJobs.userId, request.user!.id)))
        .get();
    if (!job) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return job;
  }

  function ownPage(request: FastifyRequest, reply: FastifyReply) {
    const id = Number((request.params as { id: string }).id);
    const page =
      Number.isInteger(id) &&
      db
        .select()
        .from(uploadPages)
        .where(and(eq(uploadPages.id, id), eq(uploadPages.userId, request.user!.id)))
        .get();
    if (!page) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return page;
  }

  app.addHook('preHandler', app.requireAuth);

  /** Multipart upload of 1–20 photos (field "photos"); recognition starts right away. */
  app.post('/uploads', async (request, reply) => {
    const userId = request.user!.id;
    const dir = dirFor(userId);
    await fs.promises.mkdir(dir, { recursive: true });
    const saved: Array<{ fileName: string; mediaType: string }> = [];
    try {
      for await (const part of request.files({
        limits: { fileSize: MAX_PHOTO_BYTES, files: MAX_PHOTOS },
      })) {
        const ext = MEDIA_TYPES[part.mimetype];
        if (!ext) {
          part.file.resume();
          return reply.code(415).send({ error: 'validation_error', fields: ['photos'] });
        }
        const fileName = `${crypto.randomUUID()}.${ext}`;
        const buffer = await part.toBuffer();
        await fs.promises.writeFile(path.join(dir, fileName), buffer);
        saved.push({ fileName, mediaType: part.mimetype });
      }
    } catch (err) {
      for (const f of saved) await fs.promises.rm(path.join(dir, f.fileName), { force: true });
      const code = (err as { code?: string }).code;
      if (code === 'FST_REQ_FILE_TOO_LARGE' || code === 'FST_FILES_LIMIT') {
        return reply.code(413).send({ error: 'validation_error', fields: ['photos'] });
      }
      throw err;
    }
    if (saved.length === 0)
      return reply.code(400).send({ error: 'validation_error', fields: ['photos'] });

    const jobId = db.transaction((tx) => {
      const job = tx.insert(uploadJobs).values({ userId }).returning().get();
      for (const f of saved)
        tx.insert(uploadPages)
          .values({ jobId: job.id, userId, ...f })
          .run();
      return job.id;
    });
    const pageIds = db
      .select({ id: uploadPages.id })
      .from(uploadPages)
      .where(eq(uploadPages.jobId, jobId))
      .all();
    enqueue(
      userId,
      pageIds.map((p) => p.id),
    );
    return reply.code(201).send({ job: jobDto(jobId) });
  });

  /** Uploads whose words have not been saved yet. */
  app.get('/uploads', async (request) => {
    const jobs = db
      .select({ id: uploadJobs.id })
      .from(uploadJobs)
      .where(and(eq(uploadJobs.userId, request.user!.id), isNull(uploadJobs.packageId)))
      .orderBy(desc(uploadJobs.id))
      .limit(20)
      .all();
    return { jobs: jobs.map((j) => jobDto(j.id)) };
  });

  app.get('/uploads/:id', async (request, reply) => {
    const job = ownJob(request, reply);
    if (!job) return;
    return { job: jobDto(job.id) };
  });

  app.delete('/uploads/:id', async (request, reply) => {
    const job = ownJob(request, reply);
    if (!job) return;
    if (job.packageId !== null) return reply.code(409).send({ error: 'validation_error' });
    const pages = db.select().from(uploadPages).where(eq(uploadPages.jobId, job.id)).all();
    db.delete(uploadJobs).where(eq(uploadJobs.id, job.id)).run();
    for (const p of pages)
      await fs.promises.rm(path.join(dirFor(p.userId), p.fileName), { force: true });
    return reply.code(204).send();
  });

  app.post('/uploads/pages/:id/retry', async (request, reply) => {
    const page = ownPage(request, reply);
    if (!page) return;
    db.update(uploadPages)
      .set({ status: 'pending', error: null, errorMessage: null })
      .where(eq(uploadPages.id, page.id))
      .run();
    enqueue(page.userId, [page.id]);
    return { job: jobDto(page.jobId) };
  });

  app.get('/uploads/pages/:id/photo', async (request, reply) => {
    const page = ownPage(request, reply);
    if (!page) return;
    const file = path.join(dirFor(page.userId), page.fileName);
    if (!fs.existsSync(file)) return reply.code(404).send({ error: 'not_found' });
    return reply
      .type(page.mediaType)
      .header('Cache-Control', 'private, max-age=86400')
      .send(fs.createReadStream(file));
  });

  /** Saves the (corrected) words as a new package or into an existing one; photos stay with the package. */
  app.post('/uploads/:id/save', async (request, reply) => {
    const job = ownJob(request, reply);
    if (!job) return;
    const input = parseBody(saveUploadSchema, request.body, reply);
    if (!input) return;
    const userId = request.user!.id;
    let packageId: number;
    if ('packageId' in input.target) {
      const pkg = db
        .select({ id: packages.id })
        .from(packages)
        .where(and(eq(packages.id, input.target.packageId), eq(packages.userId, userId)))
        .get();
      if (!pkg) return reply.code(404).send({ error: 'not_found' });
      packageId = pkg.id;
    } else {
      packageId = db
        .insert(packages)
        .values({ ...input.target.newPackage, userId })
        .returning({ id: packages.id })
        .get().id;
    }
    db.transaction((tx) => {
      tx.insert(vocab)
        .values(input.items.map((item) => ({ ...item, userId, packageId })))
        .run();
      tx.update(uploadJobs).set({ packageId }).where(eq(uploadJobs.id, job.id)).run();
    });
    return { packageId, count: input.items.length };
  });

  app.get('/packages/:id/photos', async (request) => {
    const id = Number((request.params as { id: string }).id);
    const rows = db
      .select({ page: uploadPages })
      .from(uploadPages)
      .innerJoin(uploadJobs, eq(uploadJobs.id, uploadPages.jobId))
      .where(and(eq(uploadJobs.packageId, id), eq(uploadJobs.userId, request.user!.id)))
      .orderBy(uploadPages.id)
      .all();
    const photos: PackagePhotoDto[] = rows.map(({ page }) => ({
      id: page.id,
      photoUrl: `/api/uploads/pages/${page.id}/photo`,
      createdAt: page.createdAt.toISOString(),
    }));
    return { photos };
  });
};
