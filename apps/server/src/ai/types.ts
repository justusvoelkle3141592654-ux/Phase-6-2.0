import type { LatencyDto, ModelInfoDto, ProviderErrorCode } from '@wordflow/shared';

export interface ChatImage {
  mediaType: string;
  base64: string;
}

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
  images?: ChatImage[];
}

export interface ChatOptions {
  model: string;
  messages: ChatMessage[];
  maxTokens?: number;
  temperature?: number;
  /** Ask reasoning models to think less (only sent when set). */
  reasoningEffort?: 'low';
  /** Ask for a JSON object (OpenAI response_format / Ollama format). */
  json?: boolean;
  signal?: AbortSignal;
  /** Ollama: how long the model stays loaded. */
  keepAlive?: string;
}

export interface ChatResult {
  text: string;
  metrics: LatencyDto;
}

export interface Provider {
  chat(options: ChatOptions): Promise<ChatResult>;
  listModels(signal?: AbortSignal): Promise<ModelInfoDto[]>;
  /** Loads the model / opens the connection so the next call is fast. */
  warmUp(model: string, signal?: AbortSignal): Promise<void>;
}

export interface ProviderConnection {
  baseUrl: string;
  apiKey?: string | null;
}

export class ProviderError extends Error {
  constructor(
    readonly code: ProviderErrorCode,
    message: string,
    readonly status?: number,
  ) {
    super(message);
  }

  toDto() {
    return { code: this.code, status: this.status, message: this.message };
  }
}

function codeForStatus(status: number): ProviderErrorCode {
  if (status === 401) return 'auth';
  if (status === 402) return 'payment';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not_found';
  if (status === 429) return 'rate_limited';
  return 'http';
}

/** Short, readable message from an error response body (JSON or text). */
async function errorText(res: Response): Promise<string> {
  const text = (await res.text().catch(() => '')).slice(0, 2000);
  try {
    const json = JSON.parse(text);
    const msg = json?.error?.message ?? json?.error ?? json?.message;
    if (typeof msg === 'string') return msg.slice(0, 300);
  } catch {
    // not JSON
  }
  return text.slice(0, 300) || res.statusText;
}

/** fetch with uniform error mapping. */
export async function request(url: string, init: RequestInit): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (err) {
    if (
      init.signal?.aborted ||
      (err as Error).name === 'AbortError' ||
      (err as Error).name === 'TimeoutError'
    ) {
      throw new ProviderError('timeout', 'Zeitüberschreitung');
    }
    const cause = (err as { cause?: { code?: string; message?: string } }).cause;
    throw new ProviderError('network', cause?.code ?? cause?.message ?? (err as Error).message);
  }
  if (!res.ok) throw new ProviderError(codeForStatus(res.status), await errorText(res), res.status);
  return res;
}

export async function readJson<T>(res: Response): Promise<T> {
  try {
    return (await res.json()) as T;
  } catch {
    throw new ProviderError('bad_response', 'Antwort ist kein gültiges JSON');
  }
}

/** Yields the lines of a streamed response body. */
export async function* lines(res: Response): AsyncGenerator<string> {
  if (!res.body) throw new ProviderError('bad_response', 'Leere Antwort');
  const decoder = new TextDecoder();
  let buffer = '';
  const reader = res.body.getReader();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let index: number;
      while ((index = buffer.indexOf('\n')) >= 0) {
        yield buffer.slice(0, index).replace(/\r$/, '');
        buffer = buffer.slice(index + 1);
      }
    }
    buffer += decoder.decode();
    if (buffer) yield buffer;
  } catch (err) {
    if (err instanceof ProviderError) throw err;
    if ((err as Error).name === 'AbortError' || (err as Error).name === 'TimeoutError') {
      throw new ProviderError('timeout', 'Zeitüberschreitung');
    }
    throw new ProviderError('network', (err as Error).message);
  } finally {
    reader.releaseLock();
  }
}

/** Measures time to first token, total time and tokens per second while streaming. */
export class Stopwatch {
  private readonly start = performance.now();
  private first: number | null = null;
  private chunks = 0;

  token(text: string) {
    if (!text) return;
    this.chunks++;
    this.first ??= performance.now();
  }

  /** `outputTokens` from the provider's usage; falls back to the number of streamed chunks. */
  finish(outputTokens?: number | null): LatencyDto {
    const end = performance.now();
    const tokens = outputTokens ?? (this.chunks || null);
    const genMs = this.first === null ? 0 : end - this.first;
    return {
      ttftMs: this.first === null ? null : Math.round(this.first - this.start),
      totalMs: Math.round(end - this.start),
      tokensPerSec: tokens && genMs > 0 ? Math.round((tokens / genMs) * 1000 * 10) / 10 : null,
    };
  }
}
