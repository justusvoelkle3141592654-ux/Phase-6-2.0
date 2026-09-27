import type { ApiErrorCode } from '@gero/shared';

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

/** Raw request against the Gero API (path starting with "/api"). */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  try {
    return await fetch(path, { credentials: 'same-origin', ...init });
  } catch {
    throw new ApiError(0, 'network_error');
  }
}

/** JSON request against the Gero API. Throws ApiError on failure. */
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
