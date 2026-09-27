import { vi } from 'vitest';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createMemoryRouter, RouterProvider } from 'react-router';
import type { UserDto } from '@gero/shared';
import { I18nProvider } from '../src/i18n';
import { routes } from '../src/router';

export const USER: UserDto = {
  id: 1,
  email: 'anna@example.org',
  name: '',
  uiLanguage: 'de',
  timezone: 'Europe/Berlin',
  setupCompleted: false,
};

type Handler = (body: unknown) => { status: number; body?: unknown };

/** Fake server: maps "METHOD /path" to a handler. Records every call. */
export function mockApi(handlers: Record<string, Handler>) {
  const calls: Array<{ key: string; body: unknown }> = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input).replace(/^\/api/, '');
    const key = `${init?.method ?? 'GET'} ${url}`;
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ key, body });
    const handler = handlers[key];
    const res = handler ? handler(body) : { status: 404, body: { error: 'not_found' } };
    return new Response(res.status === 204 ? null : JSON.stringify(res.body ?? {}), {
      status: res.status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

export function renderApp(path = '/', lang: 'de' | 'en' = 'de') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  return render(
    <QueryClientProvider client={queryClient}>
      <I18nProvider initial={lang}>
        <RouterProvider router={router} />
      </I18nProvider>
    </QueryClientProvider>,
  );
}
