import type { FastifyReply } from 'fastify';
import type { z } from 'zod';

/** Validates a request body; on failure answers 400 and returns null. */
export function parseBody<S extends z.ZodType>(
  schema: S,
  body: unknown,
  reply: FastifyReply,
): z.output<S> | null {
  const result = schema.safeParse(body ?? {});
  if (result.success) return result.data;
  void reply.code(400).send({
    error: 'validation_error',
    fields: result.error.issues.map((issue) => issue.path.join('.')),
  });
  return null;
}
