import { and, desc, eq } from 'drizzle-orm';
import {
  AI_TASKS,
  DEFAULT_TEXT_MODEL,
  PROVIDER_PRESETS,
  type AiConfigDto,
  type AiTask,
  type LatencyDto,
  type LatencyStatsDto,
  type ProviderDto,
} from '@vokabeltrainer/shared';
import type { Db } from '../db';
import { latencySamples, providerConfigs, taskModels, type ProviderConfig } from '../db/schema';
import { SecretBox } from './crypto';
import {
  createProvider,
  ProviderError,
  type ChatOptions,
  type ChatResult,
  type Provider,
} from './index';

export function toProviderDto(p: ProviderConfig): ProviderDto {
  return {
    id: p.id,
    kind: p.kind,
    name: p.name,
    baseUrl: p.baseUrl,
    protocol: p.protocol,
    hasKey: p.apiKeyEnc !== null,
    keyHint: p.keyHint,
  };
}

export interface TaskBinding {
  config: ProviderConfig;
  provider: Provider;
  model: string;
  reasoning: boolean | null;
}

function median(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid]! : Math.round((sorted[mid - 1]! + sorted[mid]!) / 2);
}

function percentile(values: number[], p: number): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)]!;
}

/** Everything about a user's AI setup: providers, task models, calls with latency tracking. */
export class AiService {
  private readonly box: SecretBox;

  constructor(
    private readonly db: Db,
    appSecret: string,
  ) {
    this.box = new SecretBox(appSecret);
  }

  /** New accounts get Pollinations with openai/gpt-oss-20b for checking and summary. */
  ensureDefaults(userId: number) {
    const existing = this.db
      .select({ id: providerConfigs.id })
      .from(providerConfigs)
      .where(eq(providerConfigs.userId, userId))
      .get();
    if (existing) return;
    const preset = PROVIDER_PRESETS.pollinations;
    this.db.transaction((tx) => {
      const p = tx
        .insert(providerConfigs)
        .values({
          userId,
          kind: 'pollinations',
          name: preset.label,
          baseUrl: preset.baseUrl,
          protocol: preset.protocol,
        })
        .returning()
        .get();
      for (const task of ['check', 'summary'] as const) {
        tx.insert(taskModels)
          .values({ userId, task, providerId: p.id, model: DEFAULT_TEXT_MODEL })
          .run();
      }
    });
  }

  config(userId: number): AiConfigDto {
    this.ensureDefaults(userId);
    const providers = this.db
      .select()
      .from(providerConfigs)
      .where(eq(providerConfigs.userId, userId))
      .orderBy(providerConfigs.id)
      .all();
    const rows = this.db.select().from(taskModels).where(eq(taskModels.userId, userId)).all();
    const tasks = Object.fromEntries(AI_TASKS.map((t) => [t, null])) as AiConfigDto['tasks'];
    for (const r of rows)
      tasks[r.task] = { providerId: r.providerId, model: r.model, reasoning: r.reasoning };
    return { providers: providers.map(toProviderDto), tasks };
  }

  getProvider(userId: number, id: number): ProviderConfig | undefined {
    return this.db
      .select()
      .from(providerConfigs)
      .where(and(eq(providerConfigs.id, id), eq(providerConfigs.userId, userId)))
      .get();
  }

  encryptKey(key: string | null | undefined) {
    if (!key) return { apiKeyEnc: null, keyHint: null };
    return { apiKeyEnc: this.box.encrypt(key), keyHint: `…${key.slice(-4)}` };
  }

  providerFor(config: ProviderConfig): Provider {
    return createProvider(config.protocol, {
      baseUrl: config.baseUrl,
      apiKey: config.apiKeyEnc ? this.box.decrypt(config.apiKeyEnc) : null,
    });
  }

  binding(userId: number, task: AiTask): TaskBinding | null {
    this.ensureDefaults(userId);
    const row = this.db
      .select({ tm: taskModels, config: providerConfigs })
      .from(taskModels)
      .innerJoin(providerConfigs, eq(providerConfigs.id, taskModels.providerId))
      .where(and(eq(taskModels.userId, userId), eq(taskModels.task, task)))
      .get();
    if (!row) return null;
    return {
      config: row.config,
      provider: this.providerFor(row.config),
      model: row.tm.model,
      reasoning: row.tm.reasoning,
    };
  }

  recordLatency(
    userId: number,
    task: AiTask | 'test',
    config: ProviderConfig,
    model: string,
    metrics: LatencyDto | null,
  ) {
    this.db
      .insert(latencySamples)
      .values({
        userId,
        task,
        providerKind: config.kind,
        model,
        ok: metrics !== null,
        ttftMs: metrics?.ttftMs ?? null,
        totalMs: metrics?.totalMs ?? null,
        tokensPerSec: metrics?.tokensPerSec ?? null,
      })
      .run();
  }

  /** Chat for a task, with latency recorded (also for failures). */
  async chat(
    userId: number,
    task: AiTask,
    options: Omit<ChatOptions, 'model'>,
    binding = this.binding(userId, task),
  ): Promise<ChatResult> {
    if (!binding)
      throw new ProviderError('not_found', 'Kein Modell für diese Aufgabe eingerichtet');
    try {
      const result = await binding.provider.chat({
        ...options,
        model: binding.model,
        reasoningEffort: binding.reasoning ? 'low' : undefined,
      });
      this.recordLatency(userId, task, binding.config, binding.model, result.metrics);
      return result;
    } catch (err) {
      this.recordLatency(userId, task, binding.config, binding.model, null);
      throw err;
    }
  }

  /** Median and slowest values of the last 100 calls per task. */
  latencyStats(userId: number): LatencyStatsDto[] {
    return (['check', 'summary', 'vision', 'test'] as const).map((task) => {
      const rows = this.db
        .select()
        .from(latencySamples)
        .where(and(eq(latencySamples.userId, userId), eq(latencySamples.task, task)))
        .orderBy(desc(latencySamples.id))
        .limit(100)
        .all();
      const ok = rows.filter((r) => r.ok);
      const totals = ok.map((r) => r.totalMs!).filter((v) => v !== null);
      const ttfts = ok.map((r) => r.ttftMs).filter((v): v is number => v !== null);
      const tps = ok.map((r) => r.tokensPerSec).filter((v): v is number => v !== null);
      return {
        task,
        count: rows.length,
        medianTtftMs: median(ttfts),
        medianTotalMs: median(totals),
        p90TotalMs: percentile(totals, 90),
        maxTotalMs: totals.length ? Math.max(...totals) : null,
        medianTokensPerSec: tps.length ? median(tps.map((v) => Math.round(v * 10)))! / 10 : null,
        failures: rows.length - ok.length,
      };
    });
  }
}
