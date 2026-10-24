import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import type { UploadJobDto } from '@wordflow/shared';
import { ADMIN, mockApi, renderApp } from './utils';

beforeEach(() => {
  localStorage.clear();
  vi.stubGlobal(
    'URL',
    Object.assign(URL, { createObjectURL: () => 'blob:x', revokeObjectURL: () => undefined }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const JOB: UploadJobDto = {
  id: 5,
  createdAt: '2026-09-27T10:00:00Z',
  packageId: null,
  pages: [
    {
      id: 11,
      status: 'done',
      error: null,
      errorMessage: null,
      photoUrl: '/api/uploads/pages/11/photo',
      entries: [
        { word: 'amicus', extra: 'amici m.', translations: ['Freund', 'Kamerad'], language: 'la' },
        { word: 'belum', extra: '', translations: ['Krieg'], language: 'la' },
      ],
    },
    {
      id: 12,
      status: 'failed',
      error: 'no_vision_model',
      errorMessage: null,
      photoUrl: '/api/uploads/pages/12/photo',
      entries: [],
    },
  ],
};

const PKG = {
  id: 7,
  name: 'Lektion 4',
  language: 'la',
  direction: 'foreign_native',
  createdAt: '',
  counts: { total: 0, inactive: 0, active: 0, due: 0, learned: 0 },
};

function packageApi(extra: Parameters<typeof mockApi>[0] = {}) {
  return mockApi({
    'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
    'GET /packages/7': () => ({ status: 200, body: { package: PKG, vocab: [] } }),
    'GET /packages/7/photos': () => ({ status: 200, body: { photos: [] } }),
    'GET /packages': () => ({ status: 200, body: { packages: [PKG] } }),
    ...extra,
  });
}

const FILES = [
  new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
  new File(['b'], 'b.jpg', { type: 'image/jpeg' }),
];

describe('Hochladen und Erkennen im Paket', () => {
  it('zeigt Kamera- und Galerie-Button mit passenden Eingaben', async () => {
    packageApi();
    renderApp('/packages/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Erkennen' }));
    expect(screen.getByRole('button', { name: 'Foto aufnehmen' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Aus Galerie' })).toBeTruthy();
    const camera = screen.getByTestId('camera-input');
    expect(camera.getAttribute('capture')).toBe('environment');
    expect(screen.getByTestId('gallery-input').hasAttribute('multiple')).toBe(true);
  });

  it('Erkennen: lädt Fotos hoch und öffnet die Vorschau mit dem Paket als Ziel', async () => {
    const calls = packageApi({
      'POST /uploads': () => ({ status: 201, body: { job: JOB } }),
      'GET /uploads/5': () => ({ status: 200, body: { job: JOB } }),
    });
    renderApp('/packages/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Erkennen' }));
    fireEvent.change(screen.getByTestId('gallery-input'), { target: { files: FILES } });
    expect(await screen.findByText('2 Fotos ausgewählt')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Hochladen und erkennen' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Erkannte Vokabeln' }),
    ).toBeTruthy();
    expect(calls.some((c) => c.key === 'POST /uploads')).toBe(true);
    await vi.waitFor(() =>
      expect((screen.getByLabelText('Paket') as HTMLSelectElement).value).toBe('7'),
    );
  });

  it('Hochladen: hängt Fotos ohne Erkennung an', async () => {
    const calls = packageApi({
      'POST /packages/7/photos': () => ({
        status: 201,
        body: { job: { ...JOB, packageId: 7 } },
      }),
    });
    renderApp('/packages/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Hochladen' }));
    fireEvent.change(screen.getByTestId('gallery-input'), { target: { files: FILES } });
    fireEvent.click(await screen.findByRole('button', { name: 'Fotos hochladen' }));
    expect(await screen.findByText('2 Fotos hochgeladen.')).toBeTruthy();
    expect(calls.some((c) => c.key === 'POST /packages/7/photos')).toBe(true);
    expect(calls.some((c) => c.key === 'POST /uploads')).toBe(false);
  });

  it('löscht ein Foto des Pakets mit dem Kreuz', async () => {
    vi.stubGlobal('confirm', () => true);
    let photos = [{ id: 11, photoUrl: '/api/uploads/pages/11/photo', createdAt: '' }];
    const calls = packageApi({
      'GET /packages/7/photos': () => ({ status: 200, body: { photos } }),
      'GET /uploads/pages/11/photo': () => ({ status: 200, body: {} }),
      'DELETE /uploads/pages/11': () => {
        photos = [];
        return { status: 204 };
      },
    });
    renderApp('/packages/7');
    fireEvent.click(await screen.findByRole('button', { name: 'Foto löschen: Foto 1' }));
    await vi.waitFor(() =>
      expect(calls.some((c) => c.key === 'DELETE /uploads/pages/11')).toBe(true),
    );
    await vi.waitFor(() => expect(screen.queryByRole('heading', { name: 'Fotos' })).toBeNull());
  });
});

describe('Vorschau und Speichern', () => {
  it('korrigiert Einträge, zeigt Fehler je Foto und speichert ins Paket', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: ADMIN } }),
      'GET /uploads/5': () => ({ status: 200, body: { job: JOB } }),
      'GET /packages': () => ({
        status: 200,
        body: { packages: [{ ...PKG, id: 3, name: 'Andere' }, PKG] },
      }),
      'POST /uploads/5/save': () => ({ status: 200, body: { packageId: 7, count: 2 } }),
      'GET /packages/7': () => ({ status: 200, body: { package: PKG, vocab: [] } }),
      'GET /packages/7/photos': () => ({ status: 200, body: { photos: [] } }),
    });
    renderApp('/upload/5?package=7');
    const word2 = (await screen.findByLabelText('Wort 2')) as HTMLInputElement;
    expect(word2.value).toBe('belum');
    expect((screen.getByLabelText('Übersetzung 1') as HTMLInputElement).value).toBe(
      'Freund; Kamerad',
    );
    expect(screen.getByText(/Es ist kein Bildmodell eingerichtet/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeTruthy();

    fireEvent.change(word2, { target: { value: 'bellum' } });
    fireEvent.click(screen.getByRole('button', { name: 'Eintrag hinzufügen' }));
    fireEvent.change(screen.getByLabelText('Wort 3'), { target: { value: 'hostis' } });
    fireEvent.change(screen.getByLabelText('Übersetzung 3'), { target: { value: 'Feind' } });
    fireEvent.click(screen.getByRole('button', { name: 'Eintrag löschen 1' }));

    // No "new package" here any more: packages are created on the packages page.
    expect(screen.queryByLabelText('Name')).toBeNull();
    fireEvent.click(await screen.findByRole('button', { name: '2 Vokabeln speichern' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Lektion 4' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /uploads/5/save')?.body).toEqual({
      packageId: 7,
      items: [
        { word: 'bellum', extra: '', translation: 'Krieg' },
        { word: 'hostis', extra: '', translation: 'Feind' },
      ],
    });
  });
});
