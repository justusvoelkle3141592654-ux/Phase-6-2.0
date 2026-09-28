import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen } from '@testing-library/react';
import type { UploadJobDto } from '@vokabeltrainer/shared';
import { mockApi, renderApp, USER } from './utils';

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

describe('Hochladen', () => {
  it('zeigt Kamera- und Galerie-Button mit passenden Eingaben', async () => {
    mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /uploads': () => ({ status: 200, body: { jobs: [] } }),
    });
    renderApp('/upload');
    expect(await screen.findByRole('button', { name: 'Foto aufnehmen' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Aus Galerie' })).toBeTruthy();
    const camera = screen.getByTestId('camera-input');
    expect(camera.getAttribute('capture')).toBe('environment');
    expect(screen.getByTestId('gallery-input').hasAttribute('multiple')).toBe(true);
  });

  it('lädt ausgewählte Fotos hoch und öffnet die Vorschau', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /uploads': () => ({ status: 200, body: { jobs: [] } }),
      'POST /uploads': () => ({ status: 201, body: { job: JOB } }),
      'GET /uploads/5': () => ({ status: 200, body: { job: JOB } }),
      'GET /packages': () => ({ status: 200, body: { packages: [] } }),
    });
    renderApp('/upload');
    const input = (await screen.findByTestId('gallery-input')) as HTMLInputElement;
    const files = [
      new File(['a'], 'a.jpg', { type: 'image/jpeg' }),
      new File(['b'], 'b.jpg', { type: 'image/jpeg' }),
    ];
    fireEvent.change(input, { target: { files } });
    expect(await screen.findByText('2 Fotos ausgewählt')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Hochladen und erkennen' }));
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Erkannte Vokabeln' }),
    ).toBeTruthy();
    expect(calls.some((c) => c.key === 'POST /uploads')).toBe(true);
  });
});

describe('Vorschau und Speichern', () => {
  it('korrigiert Einträge, zeigt Fehler je Foto und speichert als neues Paket', async () => {
    const calls = mockApi({
      'GET /auth/me': () => ({ status: 200, body: { user: USER } }),
      'GET /uploads/5': () => ({ status: 200, body: { job: JOB } }),
      'GET /packages': () => ({ status: 200, body: { packages: [] } }),
      'POST /uploads/5/save': () => ({ status: 200, body: { packageId: 9, count: 2 } }),
      'GET /packages/9': () => ({
        status: 200,
        body: {
          package: {
            id: 9,
            name: 'Lektion 4',
            language: 'la',
            direction: 'foreign_native',
            createdAt: '',
            counts: { total: 2, inactive: 2, active: 0, due: 0, learned: 0 },
          },
          vocab: [],
        },
      }),
      'GET /packages/9/photos': () => ({ status: 200, body: { photos: [] } }),
    });
    renderApp('/upload/5');
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

    expect((screen.getByLabelText('Sprache') as HTMLSelectElement).value).toBe('la');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Lektion 4' } });
    fireEvent.click(screen.getByRole('button', { name: '2 Vokabeln speichern' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Lektion 4' })).toBeTruthy();
    expect(calls.find((c) => c.key === 'POST /uploads/5/save')?.body).toEqual({
      target: { newPackage: { name: 'Lektion 4', language: 'la', direction: 'foreign_native' } },
      items: [
        { word: 'bellum', extra: '', translation: 'Krieg' },
        { word: 'hostis', extra: '', translation: 'Feind' },
      ],
    });
  });
});
