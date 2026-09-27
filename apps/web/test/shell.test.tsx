import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, screen } from '@testing-library/react';
import { mockApi, renderApp, USER } from './utils';

beforeEach(() => {
  localStorage.clear();
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('App-Rahmen', () => {
  it('zeigt die fünf Tabs', async () => {
    mockApi({ 'GET /auth/me': () => ({ status: 200, body: { user: USER } }) });
    renderApp('/');
    await screen.findByRole('heading', { level: 1, name: 'Heute' });
    for (const label of ['Start', 'Hochladen', 'Pakete', 'Lernen', 'Einstellungen']) {
      // Desktop and mobile navigation both render the tab.
      expect(screen.getAllByRole('link', { name: label })).toHaveLength(2);
    }
  });

  it('zeigt die Seite zum aktiven Tab', async () => {
    mockApi({ 'GET /auth/me': () => ({ status: 200, body: { user: USER } }) });
    renderApp('/learn');
    expect(await screen.findByRole('heading', { level: 1, name: 'Lernen' })).toBeTruthy();
  });

  it('übernimmt die Sprache aus dem Account', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: { ...USER, uiLanguage: 'en' } } }),
    });
    renderApp('/settings', 'de');
    expect(await screen.findByRole('heading', { level: 1, name: 'Settings' })).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'Decks' })).toHaveLength(2);
  });
});
