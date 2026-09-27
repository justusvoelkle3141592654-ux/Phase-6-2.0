import { z } from 'zod';

/** Wire protocols; every provider speaks one of them. */
export const PROTOCOLS = ['openai', 'anthropic', 'ollama'] as const;
export type Protocol = (typeof PROTOCOLS)[number];

export const PROVIDER_KINDS = [
  'pollinations',
  'anthropic',
  'openai',
  'openrouter',
  'ollama_local',
  'ollama_cloud',
  'custom',
] as const;
export type ProviderKind = (typeof PROVIDER_KINDS)[number];

export interface ProviderPreset {
  label: string;
  protocol: Protocol;
  /** Empty for "custom": the user enters it. */
  baseUrl: string;
  key: 'required' | 'optional' | 'none';
  /** Where to get an API key. */
  keyUrl?: string;
}

/** Base URLs include the version path; requests append e.g. "/chat/completions". */
export const PROVIDER_PRESETS: Record<ProviderKind, ProviderPreset> = {
  pollinations: {
    label: 'Pollinations.ai',
    protocol: 'openai',
    baseUrl: 'https://gen.pollinations.ai/v1',
    key: 'optional',
    keyUrl: 'https://enter.pollinations.ai',
  },
  anthropic: {
    label: 'Anthropic (Claude)',
    protocol: 'anthropic',
    baseUrl: 'https://api.anthropic.com/v1',
    key: 'required',
  },
  openai: {
    label: 'OpenAI',
    protocol: 'openai',
    baseUrl: 'https://api.openai.com/v1',
    key: 'required',
  },
  openrouter: {
    label: 'OpenRouter',
    protocol: 'openai',
    baseUrl: 'https://openrouter.ai/api/v1',
    key: 'required',
  },
  ollama_local: {
    label: 'Ollama (lokal)',
    protocol: 'ollama',
    baseUrl: 'http://localhost:11434',
    key: 'none',
  },
  ollama_cloud: {
    label: 'Ollama (online)',
    protocol: 'ollama',
    baseUrl: 'https://ollama.com',
    key: 'required',
  },
  custom: { label: 'Custom', protocol: 'openai', baseUrl: '', key: 'optional' },
};

/** Tasks that each get their own provider and model. */
export const AI_TASKS = ['check', 'summary', 'vision'] as const;
export type AiTask = (typeof AI_TASKS)[number];

/** Preset for answer checking and daily summary (no API key needed). */
export const DEFAULT_TEXT_MODEL = 'openai/gpt-oss-20b';
export const DEFAULT_AI_PROVIDER = PROVIDER_PRESETS.pollinations.label;

const baseUrl = z
  .string()
  .trim()
  .max(500)
  .refine((u) => /^https?:\/\/[^\s]+$/i.test(u), 'invalid_url')
  .transform((u) => u.replace(/\/+$/, ''));

export const providerCreateSchema = z.object({
  kind: z.enum(PROVIDER_KINDS),
  name: z.string().trim().max(60).optional(),
  baseUrl: baseUrl.optional(),
  protocol: z.enum(['openai', 'ollama']).optional(),
  apiKey: z.string().trim().max(500).optional(),
});
export type ProviderCreateInput = z.input<typeof providerCreateSchema>;

export const providerUpdateSchema = z.object({
  name: z.string().trim().max(60).optional(),
  baseUrl: baseUrl.optional(),
  protocol: z.enum(['openai', 'ollama']).optional(),
  /** New key; null removes it. */
  apiKey: z.string().trim().max(500).nullable().optional(),
});
export type ProviderUpdateInput = z.input<typeof providerUpdateSchema>;

export const taskModelSchema = z.object({
  providerId: z.int().positive(),
  model: z.string().trim().min(1).max(200),
  /** From the model list: the model reasons before answering (lower reasoning effort is requested). */
  reasoning: z.boolean().nullable().optional(),
});
export type TaskModelInput = z.input<typeof taskModelSchema>;

export const testConnectionSchema = z.object({ model: z.string().trim().max(200).optional() });

export interface ProviderDto {
  id: number;
  kind: ProviderKind;
  name: string;
  baseUrl: string;
  protocol: Protocol;
  hasKey: boolean;
  /** e.g. "…abcd"; the key itself never leaves the server. */
  keyHint: string | null;
}

export interface TaskModelDto {
  providerId: number;
  model: string;
  reasoning: boolean | null;
}

export interface AiConfigDto {
  providers: ProviderDto[];
  tasks: Record<AiTask, TaskModelDto | null>;
}

export interface ModelInfoDto {
  id: string;
  name?: string;
  /** Accepts images; null when the provider does not say. */
  vision: boolean | null;
  reasoning: boolean | null;
}

export type ProviderErrorCode =
  | 'auth'
  | 'payment'
  | 'forbidden'
  | 'not_found'
  | 'rate_limited'
  | 'timeout'
  | 'network'
  | 'bad_response'
  | 'http';

export interface ProviderErrorDto {
  code: ProviderErrorCode;
  status?: number;
  message: string;
}

export interface LatencyDto {
  /** Time to first token. */
  ttftMs: number | null;
  totalMs: number;
  tokensPerSec: number | null;
}

export interface TestResultDto {
  ok: boolean;
  /** Listing the models. */
  models?: { count: number; ms: number };
  /** A short chat with the given model. */
  chat?: LatencyDto & { reply: string };
  error?: ProviderErrorDto;
}

export interface LatencyStatsDto {
  task: AiTask | 'test';
  count: number;
  medianTtftMs: number | null;
  medianTotalMs: number | null;
  /** 90th percentile of the total time. */
  p90TotalMs: number | null;
  maxTotalMs: number | null;
  medianTokensPerSec: number | null;
  failures: number;
}
