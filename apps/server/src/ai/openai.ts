import type { ModelInfoDto } from '@gero/shared';
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

/** OpenAI Chat Completions format: OpenAI, OpenRouter, Pollinations, custom servers. */
export class OpenAiProvider implements Provider {
  constructor(private readonly conn: ProviderConnection) {}

  private headers(): Record<string, string> {
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.conn.apiKey) h.Authorization = `Bearer ${this.conn.apiKey}`;
    return h;
  }

  private static message(m: ChatMessage) {
    if (!m.images?.length) return { role: m.role, content: m.content };
    return {
      role: m.role,
      content: [
        { type: 'text', text: m.content },
        ...m.images.map((img) => ({
          type: 'image_url',
          image_url: { url: `data:${img.mediaType};base64,${img.base64}` },
        })),
      ],
    };
  }

  async chat(options: ChatOptions) {
    const body: Record<string, unknown> = {
      model: options.model,
      messages: options.messages.map(OpenAiProvider.message),
      stream: true,
    };
    if (options.maxTokens) body.max_tokens = options.maxTokens;
    if (options.temperature !== undefined) body.temperature = options.temperature;
    if (options.reasoningEffort) body.reasoning_effort = options.reasoningEffort;
    if (options.json) body.response_format = { type: 'json_object' };

    const watch = new Stopwatch();
    const res = await request(`${this.conn.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(body),
      signal: options.signal,
    });

    let text = '';
    let outputTokens: number | null = null;
    for await (const line of lines(res)) {
      if (!line.startsWith('data:')) continue;
      const data = line.slice(5).trim();
      if (data === '[DONE]') break;
      let chunk: {
        choices?: Array<{ delta?: { content?: string | null } }>;
        usage?: { completion_tokens?: number } | null;
      };
      try {
        chunk = JSON.parse(data);
      } catch {
        continue;
      }
      const delta = chunk.choices?.[0]?.delta?.content ?? '';
      if (delta) {
        watch.token(delta);
        text += delta;
      }
      if (chunk.usage?.completion_tokens) outputTokens = chunk.usage.completion_tokens;
    }
    return { text, metrics: watch.finish(outputTokens) };
  }

  async listModels(signal?: AbortSignal): Promise<ModelInfoDto[]> {
    const res = await request(`${this.conn.baseUrl}/models`, { headers: this.headers(), signal });
    const json = await readJson<{
      data?: Array<{
        id: string;
        title?: string;
        name?: string;
        category?: string;
        reasoning?: boolean;
        input_modalities?: string[];
        architecture?: { input_modalities?: string[] };
      }>;
    }>(res);
    return (
      (json.data ?? [])
        .filter((m) => typeof m.id === 'string')
        // Pollinations also lists image/audio generators; keep text models only.
        .filter((m) => m.category === undefined || m.category === 'text')
        .map((m) => {
          const modalities = m.input_modalities ?? m.architecture?.input_modalities;
          return {
            id: m.id,
            name: m.name ?? m.title,
            vision: modalities ? modalities.includes('image') : null,
            reasoning: typeof m.reasoning === 'boolean' ? m.reasoning : null,
          };
        })
    );
  }

  /** Opens the (keep-alive) connection with a cheap request. */
  async warmUp(_model: string, signal?: AbortSignal) {
    await request(`${this.conn.baseUrl}/models`, { headers: this.headers(), signal }).then((r) =>
      r.body?.cancel(),
    );
  }
}
