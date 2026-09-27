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

/** Anthropic Messages API (Claude). */
export class AnthropicProvider implements Provider {
  constructor(private readonly conn: ProviderConnection) {}

  private headers(): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      'anthropic-version': '2023-06-01',
      'x-api-key': this.conn.apiKey ?? '',
    };
  }

  private static message(m: ChatMessage) {
    if (!m.images?.length) return { role: m.role, content: m.content };
    return {
      role: m.role,
      content: [
        ...m.images.map((img) => ({
          type: 'image',
          source: { type: 'base64', media_type: img.mediaType, data: img.base64 },
        })),
        { type: 'text', text: m.content },
      ],
    };
  }

  async chat(options: ChatOptions) {
    const system = options.messages
      .filter((m) => m.role === 'system')
      .map((m) => m.content)
      .join('\n\n');
    const body: Record<string, unknown> = {
      model: options.model,
      max_tokens: options.maxTokens ?? 4096,
      messages: options.messages.filter((m) => m.role !== 'system').map(AnthropicProvider.message),
      stream: true,
    };
    if (system) body.system = system;

    const watch = new Stopwatch();
    const res = await request(`${this.conn.baseUrl}/messages`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify(body),
      signal: options.signal,
    });

    let text = '';
    let outputTokens: number | null = null;
    for await (const line of lines(res)) {
      if (!line.startsWith('data:')) continue;
      let event: {
        type?: string;
        delta?: { type?: string; text?: string };
        usage?: { output_tokens?: number };
      };
      try {
        event = JSON.parse(line.slice(5).trim());
      } catch {
        continue;
      }
      if (
        event.type === 'content_block_delta' &&
        event.delta?.type === 'text_delta' &&
        event.delta.text
      ) {
        watch.token(event.delta.text);
        text += event.delta.text;
      } else if (event.type === 'message_delta' && event.usage?.output_tokens) {
        outputTokens = event.usage.output_tokens;
      } else if (event.type === 'message_stop') {
        break;
      }
    }
    return { text, metrics: watch.finish(outputTokens) };
  }

  async listModels(signal?: AbortSignal): Promise<ModelInfoDto[]> {
    const models: ModelInfoDto[] = [];
    let after: string | undefined;
    for (let page = 0; page < 10; page++) {
      const url = `${this.conn.baseUrl}/models?limit=100${after ? `&after_id=${encodeURIComponent(after)}` : ''}`;
      const json = await readJson<{
        data?: Array<{
          id: string;
          display_name?: string;
          capabilities?: { image_input?: { supported?: boolean } };
        }>;
        has_more?: boolean;
        last_id?: string;
      }>(await request(url, { headers: this.headers(), signal }));
      for (const m of json.data ?? []) {
        const vision = m.capabilities?.image_input?.supported;
        models.push({
          id: m.id,
          name: m.display_name,
          vision: typeof vision === 'boolean' ? vision : null,
          reasoning: null,
        });
      }
      if (!json.has_more || !json.last_id) break;
      after = json.last_id;
    }
    return models;
  }

  async warmUp(_model: string, signal?: AbortSignal) {
    await request(`${this.conn.baseUrl}/models?limit=1`, { headers: this.headers(), signal }).then(
      (r) => r.body?.cancel(),
    );
  }
}
