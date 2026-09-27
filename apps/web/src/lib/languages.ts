import { LANGUAGE_PRESETS, type LanguagePreset } from '@gero/shared';
import type { Messages } from '../i18n/de';

export function isPreset(language: string): language is LanguagePreset {
  return (LANGUAGE_PRESETS as readonly string[]).includes(language);
}

/** Display name: presets are translated, free text is shown as entered. */
export function languageName(m: Messages, language: string): string {
  return isPreset(language) ? m.packages.languages[language] : language;
}
