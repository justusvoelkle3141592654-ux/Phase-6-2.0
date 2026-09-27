import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { I18nProvider } from '../src/i18n';
import { routes } from '../src/router';

afterEach(cleanup);

function renderAt(path: string, lang: 'de' | 'en' = 'de') {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  return render(
    <I18nProvider initial={lang}>
      <RouterProvider router={router} />
    </I18nProvider>,
  );
}

describe('App-Rahmen', () => {
  it('zeigt die fünf Tabs', () => {
    renderAt('/');
    for (const label of ['Start', 'Hochladen', 'Pakete', 'Lernen', 'Einstellungen']) {
      // Desktop and mobile navigation both render the tab.
      expect(screen.getAllByRole('link', { name: label })).toHaveLength(2);
    }
  });

  it('zeigt die Seite zum aktiven Tab', () => {
    renderAt('/learn');
    expect(screen.getByRole('heading', { level: 1, name: 'Lernen' })).toBeTruthy();
  });

  it('schaltet auf Englisch um', () => {
    renderAt('/settings', 'en');
    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'Decks' })).toHaveLength(2);
  });
});
