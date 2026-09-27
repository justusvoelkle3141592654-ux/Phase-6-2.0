import type { Protocol } from '@gero/shared';
import { AnthropicProvider } from './anthropic';
import { OllamaProvider } from './ollama';
import { OpenAiProvider } from './openai';
import type { Provider, ProviderConnection } from './types';

export function createProvider(protocol: Protocol, conn: ProviderConnection): Provider {
  switch (protocol) {
    case 'anthropic':
      return new AnthropicProvider(conn);
    case 'ollama':
      return new OllamaProvider(conn);
    default:
      return new OpenAiProvider(conn);
  }
}

export * from './types';
