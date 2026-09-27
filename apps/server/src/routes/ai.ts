import type { FastifyPluginAsync, FastifyReply, FastifyRequest } from 'fastify';
import { and, eq } from 'drizzle-orm';
import {
  AI_TASKS,
  PROVIDER_PRESETS,
  providerCreateSchema,
  providerUpdateSchema,
  taskModelSchema,
  testConnectionSchema,
  type AiTask,
  type TestResultDto,
} from '@gero/shared';
import type { Db } from '../db';
import { providerConfigs, taskModels, type ProviderConfig } from '../db/schema';
import { ProviderError } from '../ai';
import { toProviderDto, type AiService } from '../ai/service';
import { parseBody } from './parse';

const TEST_TIMEOUT_MS = 30_000;

export const aiRoutes: FastifyPluginAsync<{ db: Db; ai: AiService }> = async (app, { db, ai }) => {
  app.addHook('preHandler', app.requireAuth);

  function ownProvider(request: FastifyRequest, reply: FastifyReply): ProviderConfig | null {
    const id = Number((request.params as { id: string }).id);
    const config = Number.isInteger(id) ? ai.getProvider(request.user!.id, id) : undefined;
    if (!config) {
      void reply.code(404).send({ error: 'not_found' });
      return null;
    }
    return config;
  }

  app.get('/ai/config', async (request) => ai.config(request.user!.id));

  app.post('/ai/providers', async (request, reply) => {
    const input = parseBody(providerCreateSchema, request.body, reply);
    if (!input) return;
    const preset = PROVIDER_PRESETS[input.kind];
    const baseUrl = input.kind === 'custom' || input.baseUrl ? input.baseUrl : preset.baseUrl;
    if (!baseUrl) return reply.code(400).send({ error: 'validation_error', fields: ['baseUrl'] });
    const created = db
      .insert(providerConfigs)
      .values({
        userId: request.user!.id,
        kind: input.kind,
        name:
          input.name ||
          (input.kind === 'custom' ? baseUrl.replace(/^https?:\/\//, '') : preset.label),
        baseUrl,
        protocol: input.kind === 'custom' ? (input.protocol ?? 'openai') : preset.protocol,
        ...ai.encryptKey(input.apiKey),
      })
      .returning()
      .get();
    return reply.code(201).send({ provider: toProviderDto(created) });
  });

  app.patch('/ai/providers/:id', async (request, reply) => {
    const config = ownProvider(request, reply);
    if (!config) return;
    const input = parseBody(providerUpdateSchema, request.body, reply);
    if (!input) return;
    const changes: Partial<ProviderConfig> = {};
    if (input.name) changes.name = input.name;
    if (input.baseUrl) changes.baseUrl = input.baseUrl;
    if (input.protocol && config.kind === 'custom') changes.protocol = input.protocol;
    if (input.apiKey !== undefined) Object.assign(changes, ai.encryptKey(input.apiKey));
    const updated =
      Object.keys(changes).length === 0
        ? config
        : db
            .update(providerConfigs)
            .set(changes)
            .where(eq(providerConfigs.id, config.id))
            .returning()
            .get();
    return { provider: toProviderDto(updated) };
  });

  app.delete('/ai/providers/:id', async (request, reply) => {
    const config = ownProvider(request, reply);
    if (!config) return;
    db.delete(providerConfigs).where(eq(providerConfigs.id, config.id)).run();
    return reply.code(204).send();
  });

  /** Live model list; `?vision=1` keeps only models that accept images (or do not say). */
  app.get('/ai/providers/:id/models', async (request, reply) => {
    const config = ownProvider(request, reply);
    if (!config) return;
    try {
      const models = await ai.providerFor(config).listModels(AbortSignal.timeout(TEST_TIMEOUT_MS));
      const visionOnly = (request.query as { vision?: string }).vision === '1';
      return { models: visionOnly ? models.filter((m) => m.vision !== false) : models };
    } catch (err) {
      if (err instanceof ProviderError)
        return reply.code(502).send({ error: 'provider_error', provider: err.toDto() });
      throw err;
    }
  });

  /** Lists the models and, with a model, sends a tiny chat; reports latency. */
  app.post('/ai/providers/:id/test', async (request, reply) => {
    const config = ownProvider(request, reply);
    if (!config) return;
    const input = parseBody(testConnectionSchema, request.body, reply);
    if (!input) return;
    const provider = ai.providerFor(config);
    const result: TestResultDto = { ok: false };
    try {
      const started = performance.now();
      const models = await provider.listModels(AbortSignal.timeout(TEST_TIMEOUT_MS));
      result.models = { count: models.length, ms: Math.round(performance.now() - started) };
      if (input.model) {
        const chat = await provider.chat({
          model: input.model,
          messages: [{ role: 'user', content: 'Reply with the single word: OK' }],
          maxTokens: 256,
          signal: AbortSignal.timeout(TEST_TIMEOUT_MS),
        });
        result.chat = { ...chat.metrics, reply: chat.text.trim().slice(0, 100) };
        ai.recordLatency(request.user!.id, 'test', config, input.model, chat.metrics);
      }
      result.ok = true;
    } catch (err) {
      if (!(err instanceof ProviderError)) throw err;
      result.error = err.toDto();
      if (input.model) ai.recordLatency(request.user!.id, 'test', config, input.model, null);
    }
    return result;
  });

  app.put('/ai/tasks/:task', async (request, reply) => {
    const task = (request.params as { task: string }).task as AiTask;
    if (!AI_TASKS.includes(task)) return reply.code(404).send({ error: 'not_found' });
    const input = parseBody(taskModelSchema, request.body, reply);
    if (!input) return;
    if (!ai.getProvider(request.user!.id, input.providerId))
      return reply.code(404).send({ error: 'not_found' });
    const values = {
      providerId: input.providerId,
      model: input.model,
      reasoning: input.reasoning ?? null,
    };
    db.insert(taskModels)
      .values({ userId: request.user!.id, task, ...values })
      .onConflictDoUpdate({ target: [taskModels.userId, taskModels.task], set: values })
      .run();
    return ai.config(request.user!.id);
  });

  app.delete('/ai/tasks/:task', async (request, reply) => {
    const task = (request.params as { task: string }).task as AiTask;
    if (!AI_TASKS.includes(task)) return reply.code(404).send({ error: 'not_found' });
    db.delete(taskModels)
      .where(and(eq(taskModels.userId, request.user!.id), eq(taskModels.task, task)))
      .run();
    return ai.config(request.user!.id);
  });

  app.get('/ai/latency', async (request) => ({ stats: ai.latencyStats(request.user!.id) }));
};
