import { recognitionSchema, type RecognizedEntry } from '@wordflow/shared';
import type { AiService } from '../ai/service';
import { ProviderError, type ChatMessage } from '../ai';

const VISION_TIMEOUT_MS = 180_000;

const SYSTEM_PROMPT =
  'Du liest Fotos aus einem handgeschriebenen Vokabelheft. Die Muttersprache ist Deutsch. ' +
  'Antworte ausschließlich mit einem JSON-Objekt, ohne Erklärungen.';

const USER_PROMPT =
  'Lies alle Vokabeln auf dem Foto. Gib dieses JSON zurück:\n' +
  '{"entries": [{"word": "Fremdwort in der Grundform", ' +
  '"extra": "Zusatzangaben wie Stammformen, Genus oder Plural, sonst leer", ' +
  '"translations": ["deutsche Übersetzung", "…"], ' +
  '"language": "Sprachcode der Fremdsprache, z. B. en, fr oder la"}]}';

const RETRY_PROMPT =
  'Die Antwort war kein gültiges JSON im verlangten Format. Antworte nur mit dem JSON-Objekt {"entries": [...]}.';

/** Finds the JSON object in a reply (models sometimes wrap it in ```json fences). */
export function extractJson(text: string): unknown {
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return null;
  try {
    return JSON.parse(text.slice(start, end + 1));
  } catch {
    return null;
  }
}

export function parseRecognition(text: string): RecognizedEntry[] | null {
  const result = recognitionSchema.safeParse(extractJson(text));
  return result.success ? result.data.entries : null;
}

export class RecognitionError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/** Reads one photo with the user's vision model; one retry when the format is wrong. */
export async function recognize(
  ai: AiService,
  userId: number,
  image: { mediaType: string; base64: string },
): Promise<RecognizedEntry[]> {
  const binding = ai.binding(userId, 'vision');
  if (!binding) throw new RecognitionError('no_vision_model', 'Kein Bildmodell eingerichtet');
  const messages: ChatMessage[] = [
    { role: 'system', content: SYSTEM_PROMPT },
    { role: 'user', content: USER_PROMPT, images: [image] },
  ];
  try {
    for (let attempt = 0; attempt < 2; attempt++) {
      const result = await ai.chat(
        userId,
        'vision',
        {
          messages,
          maxTokens: 8000,
          temperature: 0,
          signal: AbortSignal.timeout(VISION_TIMEOUT_MS),
        },
        binding,
      );
      const entries = parseRecognition(result.text);
      if (entries) return entries;
      messages.push(
        { role: 'assistant', content: result.text },
        { role: 'user', content: RETRY_PROMPT },
      );
    }
  } catch (err) {
    if (err instanceof ProviderError) throw new RecognitionError(err.code, err.message);
    throw err;
  }
  throw new RecognitionError(
    'invalid_format',
    'Die Antwort des Modells hatte zweimal das falsche Format',
  );
}
