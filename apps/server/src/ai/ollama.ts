import type { ModelInfoDto } from '@wordflow/shared';
import {
  lines,
  readJson,
  request,
  Stopwatch,
  type ChatMessage,
  type ChatOptions,
  type Provider,
  type ProviderConnection,
} from './types';

/** Ollama API (/api/chat), local or online (ollama.com with API key). */
export class OllamaProvider implements Provider {
  constructor(private readonly conn: ProviderConnection) {}

  private headers(): Record<string, string> {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.conn.apiKey) h.Authorization = `Bearer ${this.conn.apiKey}`;
    return h;
  }

  private static message(m: ChatMessage) {
    return m.images?.length
      ? { role: m.role, content: m.content, images: m.images.map((img) => img.base64) }
      : { role: m.role, content: m.content };
  }

  async chat(options: ChatOptions) {
    const modelOptions: Record<string, unknown> = {};
    if (options.maxTokens) modelOptions.num_predict = options.maxTokens;
    if (options.temperature !== undefined) modelOptions.temperature = options.temperature;
    const body: Record<string, unknown> = {
      model: options.model,
      messages: options.messages.map(OllamaProvider.message),
      stream: true,
      options: modelOptions,
    };
    if (options.keepAlive) body.keep_alive = options.keepAlive;
    if (options.json) body.format = 'json';
    if (options.reasoningEffort) body.think = options.reasoningEffort;

    const watch = new Stopwatch();
    const res = await request(`${this.conn.baseUrl}/api/chat`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(body),
      signal: options.signal,
    });

    let text = '';
    let outputTokens: number | null = null;
    for await (const line of lines(res)) {
      if (!line.trim()) continue;
      let chunk: {
        message?: { content?: string };
        done?: boolean;
        eval_count?: number;
        error?: string;
      };
      try {
        chunk = JSON.parse(line);
      } catch {
        continue;
      }
      const delta = chunk.message?.content ?? '';
      if (delta) {
        watch.token(delta);
        text += delta;
      }
      if (chunk.done) {
        outputTokens = chunk.eval_count ?? null;
        break;
      }
    }
    return { text, metrics: watch.finish(outputTokens) };
  }

  async listModels(signal?: AbortSignal): Promise<ModelInfoDto[]> {
    const json = await readJson<{ models?: Array<{ name: string }> }>(
      await request(`${this.conn.baseUrl}/api/tags`, { headers: this.headers(), signal }),
    );
    const names = (json.models ?? []).map((m) => m.name).filter(Boolean);
    // Capabilities (vision, completion) come from /api/show; a few at a time.
    const results: ModelInfoDto[] = [];
    for (let i = 0; i < names.length; i += 8) {
      const batch = await Promise.all(
        names.slice(i, i + 8).map((name) => this.describe(name, signal)),
      );
      results.push(...batch.filter((m): m is ModelInfoDto => m !== null));
    }
    return results;
  }

  private async describe(name: string, signal?: AbortSignal): Promise<ModelInfoDto | null> {
    try {
      const info = await readJson<{ capabilities?: string[] }>(
        await request(`${this.conn.baseUrl}/api/show`, {
          method: 'POST',
          headers: this.headers(),
          body: JSON.stringify({ model: name }),
          signal,
        }),
      );
      const caps = info.capabilities;
      // Embedding-only models cannot chat.
      if (caps && !caps.includes('completion')) return null;
      return {
        id: name,
        vision: caps ? caps.includes('vision') : null,
        reasoning: caps ? caps.includes('thinking') : null,
      };
    } catch {
      return { id: name, vision: null, reasoning: null };
    }
  }

  /** An empty message list loads the model into memory. */
  async warmUp(model: string, signal?: AbortSignal) {
    const res = await request(`${this.conn.baseUrl}/api/chat`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ model, messages: [], keep_alive: '30m' }),
      signal,
    });
    await res.body?.cancel();
  }
}
