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

/** JSON request against the Gero API. Throws ApiError on failure. */
export async function api<T>(
  path: string,
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method: init.method ?? 'GET',
      credentials: 'same-origin',
      headers: init.body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
    });
  } catch {
    throw new ApiError(0, 'network_error');
  }
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.error ?? 'internal_error', data ?? undefined);
  return data as T;
}
