import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import type { PackageDto, VocabDto } from '@wordflow/shared';
import { ADMIN, mockApi, renderApp, USER } from './utils';

beforeEach(() => localStorage.clear());
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const counts = { total: 0, inactive: 0, active: 0, due: 0, learned: 0 };
const PKG: PackageDto = {
  id: 7,
  name: 'Unit 1',
  language: 'la',
  direction: 'foreign_native',
  createdAt: '',
  counts,
};

function type(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

describe('Pakete', () => {
  it('zeigt die Pakete mit Sprache und Fälligkeit', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /packages': () => ({
        status: 200,
        body: { packages: [{ ...PKG, counts: { ...counts, total: 12, due: 3, inactive: 2 } }] },
      }),
    });
    renderApp('/packages');
    expect(await screen.findByText('Unit 1')).toBeTruthy();
    expect(screen.getByText('Latein · 12 Vokabeln · 2 inaktiv')).toBeTruthy();
    expect(screen.getByText('3 fällig')).toBeTruthy();
  });

  it('normale Accounts können keine Pakete anlegen', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /packages': () => ({ status: 200, body: { packages: [] } }),
    });
    renderApp('/packages');
    expect(await screen.findByText(/Der Superuser legt die Pakete an/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Neues Paket' })).toBeNull();
  });

  it('legt ein Paket mit eigener Sprache an und öffnet es', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
      'GET /packages': () => ({ status: 200, body: { packages: [] } }),
      'POST /packages': (body) => ({
        status: 201,
        body: { package: { ...PKG, ...(body as object) } },
      }),
      'GET /packages/7': () => ({
        status: 200,
        body: {
          package: { ...PKG, name: 'Lektion 1', language: 'Spanisch', direction: 'random' },
          vocab: [],
        },
      }),
    });
    renderApp('/packages');
    fireEvent.click(await screen.findByRole('button', { name: 'Neues Paket' }));
    type('Name', 'Lektion 1');
    type('Sprache', '__other__');
    type('Andere Sprache', 'Spanisch');
    type('Abfragerichtung', 'random');
    fireEvent.click(screen.getAllByRole('button', { name: 'Neues Paket' }).at(-1)!);
    expect(await screen.findByRole('heading', { level: 1, name: 'Lektion 1' })).toBeTruthy();
    expect(screen.getByText(/Spanisch · Zufällig/)).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /packages')?.body).toEqual({
      name: 'Lektion 1',
      language: 'Spanisch',
      direction: 'random',
    });
  });
});

describe('Paket-Detail', () => {
  it('fügt Vokabeln hinzu und aktiviert alle', async () => {
    let vocab: VocabDto[] = [];
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
      'GET /packages/7': () => ({
        status: 200,
        body: {
          package: {
            ...PKG,
            counts: {
              ...counts,
              total: vocab.length,
              inactive: vocab.filter((v) => !v.active).length,
            },
          },
          vocab,
        },
      }),
      'POST /packages/7/vocab': (body) => {
        const items = (body as { items: VocabDto[] }).items;
        vocab = items.map((item, i) => ({
          ...item,
          id: i + 1,
          packageId: 7,
          active: false,
          stage: 1,
          dueDate: null,
          learned: false,
        }));
        return { status: 201, body: { vocab } };
      },
      'POST /packages/7/activate': () => {
        vocab = vocab.map((v) => ({ ...v, active: true, dueDate: '2026-09-27' }));
        return { status: 200, body: { activated: 1 } };
      },
    });
    renderApp('/packages/7');
    await screen.findByRole('heading', { level: 1, name: 'Unit 1' });
    expect(screen.getByText('Noch keine Vokabeln in diesem Paket.')).toBeTruthy();

    type('Wort', 'amicus');
    type('Zusatz', 'amici m.');
    type('Übersetzung', 'Freund');
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }));

    expect(await screen.findByText('amicus')).toBeTruthy();
    expect(screen.getByText('Inaktiv')).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /packages/7/vocab')?.body).toEqual({
      items: [{ word: 'amicus', extra: 'amici m.', translation: 'Freund' }],
    });

    fireEvent.click(screen.getByRole('button', { name: 'Alle aktivieren (1)' }));
    expect(await screen.findByText('Stufe 1')).toBeTruthy();
  });

  it('Superuser: neben „Hinzufügen“ gibt es „Hochladen“ und „Erkennen“', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
      'GET /packages/7': () => ({ status: 200, body: { package: PKG, vocab: [] } }),
    });
    renderApp('/packages/7');
    await screen.findByRole('heading', { level: 1, name: 'Unit 1' });
    expect(screen.getByRole('button', { name: 'Hinzufügen' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Hochladen' }));
    expect(screen.getByText(/nur an das Paket angehängt/)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Erkennen' }));
    expect(screen.getByText(/automatisch erkannt/)).toBeTruthy();
  });

  it('User: trennt eigene (aktive) und Superuser-Vokabeln (inaktiv), nur Aktivieren', async () => {
    const word = (id: number, w: string, active: boolean): VocabDto => ({
      id,
      packageId: 7,
      word: w,
      extra: '',
      translation: w.toUpperCase(),
      active,
      stage: 1,
      dueDate: active ? '2026-09-27' : null,
      learned: false,
    });
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /packages/7': () => ({
        status: 200,
        body: {
          package: { ...PKG, counts: { ...counts, total: 3, inactive: 2, active: 1 } },
          vocab: [word(1, 'amicus', true), word(2, 'bellum', false), word(3, 'hostis', false)],
        },
      }),
      'POST /vocab/2/activate': () => ({ status: 200, body: { vocab: word(2, 'bellum', true) } }),
    });
    renderApp('/packages/7');
    expect(
      await screen.findByRole('heading', { level: 2, name: 'Meine Vokabeln (aktiv) · 1' }),
    ).toBeTruthy();
    expect(
      screen.getByRole('heading', { level: 2, name: 'Vokabeln vom Superuser (inaktiv) · 2' }),
    ).toBeTruthy();
    // No adding, editing or deleting for normal accounts.
    expect(screen.queryByRole('button', { name: 'Hinzufügen' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hochladen' })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Bearbeiten/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Löschen/ })).toBeNull();
    expect(screen.getByRole('button', { name: 'Alle aktivieren (2)' })).toBeTruthy();

    fireEvent.click(screen.getAllByRole('button', { name: 'Aktivieren' })[0]!);
    await vi.waitFor(() =>
      expect(calls.some((c) => c.key === 'POST /vocab/2/activate')).toBe(true),
    );
  });
});
