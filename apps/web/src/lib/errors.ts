import type { Messages } from '../i18n/de';
import { ApiError } from './api';

/** User-facing message for a failed API call. */
export function errorMessage(m: Messages, error: unknown): string {
  const code = error instanceof ApiError ? error.code : 'unknown';
  const known = m.auth.errors as Record<string, string>;
  return known[code] ?? m.auth.errors.unknown;
}
