import { DEFAULT_AI_TIMEOUT_MS, normalize, type CardDirection } from '@vokabeltrainer/shared';
import type { AiService } from '../ai/service';
import type { Db } from '../db';
import { getSettings } from '../routes/settings';
import type { AiCheck } from './decide';

const LANGUAGE_NAMES: Record<string, string> = { en: 'Englisch', fr: 'Französisch', la: 'Latein' };

const SYSTEM_PROMPT =
  'Du prüfst Vokabelantworten. Antworte mit genau einem Wort: "richtig", "tippfehler" oder "falsch". ' +
  'Synonyme und sinngleiche Antworten sind richtig. Kleine Rechtschreibfehler sind "tippfehler".';

export function checkPrompt(input: {
  language: string;
  direction: CardDirection;
  prompt: string;
  solution: string;
  answer: string;
}): string {
  const lang = LANGUAGE_NAMES[input.language] ?? input.language;
  const asked = input.direction === 'foreign_native' ? `${lang} → Deutsch` : `Deutsch → ${lang}`;
  return `Richtung: ${asked}\nWort: ${input.prompt}\nLösung: ${input.solution}\nAntwort: ${input.answer}`;
}

/** Reads the one-word verdict; anything else counts as "no verdict". */
export function parseVerdict(text: string): 'correct' | 'typo' | 'wrong' | null {
  const first = normalize(text).split(/[\s.,;:!?"'„“]+/)[0] ?? '';
  if (['richtig', 'correct', 'right'].includes(first)) return 'correct';
  if (['tippfehler', 'typo'].includes(first)) return 'typo';
  if (['falsch', 'wrong', 'incorrect'].includes(first)) return 'wrong';
  return null;
}

/** AI answer check for a user, or undefined when no model is set up for checking. */
export function aiCheckFor(
  db: Db,
  ai: AiService,
  userId: number,
  language: string,
): AiCheck | undefined {
  const binding = ai.binding(userId, 'check');
  if (!binding) return undefined;
  const timeoutMs = getSettings(db, userId).aiTimeoutMs ?? DEFAULT_AI_TIMEOUT_MS;
  return async ({ vocab, direction, answer, solution }) => {
    const prompt = direction === 'foreign_native' ? vocab.word : vocab.translation;
    try {
      const result = await ai.chat(
        userId,
        'check',
        {
          messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            {
              role: 'user',
              content: checkPrompt({ language, direction, prompt, solution, answer }),
            },
          ],
          // One word is enough, but reasoning models need room to think.
          maxTokens: binding.reasoning === false ? 8 : 400,
          temperature: 0,
          keepAlive: '30m',
          signal: AbortSignal.timeout(timeoutMs),
        },
        binding,
      );
      return parseVerdict(result.text);
    } catch {
      return null;
    }
  };
}
