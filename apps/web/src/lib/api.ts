import type { ApiErrorCode } from '@vokabeltrainer/shared';
import { getServerUrl, getToken, isApp } from './platform';

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: ApiErrorCode | 'network_error' | 'provider_error',
    /** Full error body, e.g. `{ provider: { code, message } }`. */
    readonly body?: Record<string, unknown>,
  ) {
    super(code);
  }
}

/**
 * Raw request against the Vokabeltrainer API (path starting with "/api"). The browser
 * uses the session cookie; the Android app calls the configured server with
 * its bearer token.
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  try {
    if (!isApp) return await fetch(path, { credentials: 'same-origin', ...init });
    const headers = new Headers(init.headers);
    const token = getToken();
    if (token) headers.set('Authorization', `Bearer ${token}`);
    return await fetch(`${getServerUrl() ?? ''}${path}`, { ...init, headers, credentials: 'omit' });
  } catch {
    throw new ApiError(0, 'network_error');
  }
}

/** JSON request against the Vokabeltrainer API. Throws ApiError on failure. */
export async function api<T>(
  path: string,
  init: { method?: string; body?: unknown; form?: FormData } = {},
): Promise<T> {
  const res = await apiFetch(`/api${path}`, {
    method: init.method ?? 'GET',
    headers: init.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: init.form ?? (init.body === undefined ? undefined : JSON.stringify(init.body)),
  });
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.error ?? 'internal_error', data ?? undefined);
  return data as T;
}
