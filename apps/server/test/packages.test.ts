import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { todayIn } from '@wordflow/shared';
import { adminHeaders, ORIGIN, register, sessionCookie, testApp } from './helpers';

let app: FastifyInstance;
let headers: Record<string, string>;

beforeEach(async () => {
  app = await testApp();
  // The superuser maintains the packages.
  headers = await adminHeaders(app);
});
afterEach(async () => {
  await app.close();
});

function req(
  method: 'GET' | 'POST' | 'PATCH' | 'DELETE',
  url: string,
  payload?: object,
  h = headers,
) {
  return app.inject({ method, url: `/api${url}`, headers: h, payload });
}

async function createPackage(body: object = { name: 'Unit 1', language: 'en' }) {
  return (await req('POST', '/packages', body)).json().package;
}

describe('Vokabelpakete', () => {
  it('legt Pakete an, listet, ändert und löscht sie', async () => {
    const res = await req('POST', '/packages', {
      name: ' Unit 1 ',
      language: 'la',
      direction: 'random',
    });
    expect(res.statusCode).toBe(201);
    const pkg = res.json().package;
    expect(pkg).toMatchObject({ name: 'Unit 1', language: 'la', direction: 'random' });
    expect(pkg.counts).toEqual({ total: 0, inactive: 0, active: 0, due: 0, learned: 0 });

    const list = await req('GET', '/packages');
    expect(list.json().packages).toHaveLength(1);

    const patched = await req('PATCH', `/packages/${pkg.id}`, {
      name: 'Lektion 1',
      language: 'Spanisch',
    });
    expect(patched.json().package).toMatchObject({ name: 'Lektion 1', language: 'Spanisch' });

    expect((await req('DELETE', `/packages/${pkg.id}`)).statusCode).toBe(204);
    expect((await req('GET', `/packages/${pkg.id}`)).statusCode).toBe(404);
  });

  it('Standardrichtung ist Fremdsprache → Deutsch', async () => {
    expect((await createPackage()).direction).toBe('foreign_native');
  });

  it('prüft die Eingaben', async () => {
    const res = await req('POST', '/packages', { name: '', language: '', direction: 'quer' });
    expect(res.statusCode).toBe(400);
    expect(res.json().fields).toEqual(expect.arrayContaining(['name', 'language', 'direction']));
  });
});

describe('Vokabeln', () => {
  it('fügt Vokabeln hinzu; neue Vokabeln sind inaktiv', async () => {
    const pkg = await createPackage();
    const res = await req('POST', `/packages/${pkg.id}/vocab`, {
      items: [
        { word: 'house', translation: 'Haus' },
        { word: 'amicus', extra: 'amici m.', translation: 'Freund' },
      ],
    });
    expect(res.statusCode).toBe(201);
    const items = res.json().vocab;
    expect(items).toHaveLength(2);
    expect(items[0]).toMatchObject({
      word: 'house',
      extra: '',
      active: false,
      dueDate: null,
      learned: false,
    });
    expect(items[1].extra).toBe('amici m.');

    const detail = (await req('GET', `/packages/${pkg.id}`)).json();
    expect(detail.vocab).toHaveLength(2);
    expect(detail.package.counts).toMatchObject({ total: 2, inactive: 2, due: 0 });
  });

  it('bearbeitet und löscht eine Vokabel', async () => {
    const pkg = await createPackage();
    const [v] = (
      await req('POST', `/packages/${pkg.id}/vocab`, {
        items: [{ word: 'hous', translation: 'Haus' }],
      })
    ).json().vocab;
    const patched = await req('PATCH', `/vocab/${v.id}`, {
      word: 'house',
      translation: 'Haus; Gebäude',
    });
    expect(patched.json().vocab).toMatchObject({ word: 'house', translation: 'Haus; Gebäude' });
    expect((await req('DELETE', `/vocab/${v.id}`)).statusCode).toBe(204);
    expect((await req('GET', `/packages/${pkg.id}`)).json().vocab).toHaveLength(0);
  });

  it('aktiviert einzeln: Stufe 1, heute fällig', async () => {
    const pkg = await createPackage();
    const [v] = (
      await req('POST', `/packages/${pkg.id}/vocab`, {
        items: [{ word: 'dog', translation: 'Hund' }],
      })
    ).json().vocab;
    const res = await req('POST', `/vocab/${v.id}/activate`);
    expect(res.json().vocab).toMatchObject({
      active: true,
      stage: 1,
      dueDate: todayIn('Europe/Berlin'),
    });
  });

  it('aktiviert ein ganzes Paket', async () => {
    const pkg = await createPackage();
    await req('POST', `/packages/${pkg.id}/vocab`, {
      items: [
        { word: 'a', translation: 'A' },
        { word: 'b', translation: 'B' },
      ],
    });
    const res = await req('POST', `/packages/${pkg.id}/activate`);
    expect(res.json().activated).toBe(2);
    expect(res.json().package.counts).toMatchObject({ total: 2, inactive: 0, active: 2, due: 2 });
  });

  it('Pakete sind für alle sichtbar, nur der Superuser darf sie ändern', async () => {
    const pkg = await createPackage();
    const [v] = (
      await req('POST', `/packages/${pkg.id}/vocab`, { items: [{ word: 'x', translation: 'y' }] })
    ).json().vocab;
    const bob = {
      origin: ORIGIN,
      cookie: `wordflow_session=${sessionCookie(await register(app, { email: 'bob@example.org' }))}`,
    };
    expect((await req('GET', '/packages', undefined, bob)).json().packages).toHaveLength(1);
    expect((await req('GET', `/packages/${pkg.id}`, undefined, bob)).statusCode).toBe(200);
    expect((await req('POST', '/packages', { name: 'B', language: 'en' }, bob)).statusCode).toBe(
      403,
    );
    expect((await req('PATCH', `/packages/${pkg.id}`, { name: 'z' }, bob)).statusCode).toBe(403);
    expect((await req('PATCH', `/vocab/${v.id}`, { word: 'z' }, bob)).statusCode).toBe(403);
    expect((await req('DELETE', `/vocab/${v.id}`, undefined, bob)).statusCode).toBe(403);
    expect((await req('DELETE', `/packages/${pkg.id}`, undefined, bob)).statusCode).toBe(403);
    expect(
      (
        await req(
          'POST',
          `/packages/${pkg.id}/vocab`,
          { items: [{ word: 'a', translation: 'b' }] },
          bob,
        )
      ).statusCode,
    ).toBe(403);
  });

  it('mehrere Accounts lernen dieselben Vokabeln mit eigenem Fortschritt', async () => {
    const pkg = await createPackage();
    const [a, b] = (
      await req('POST', `/packages/${pkg.id}/vocab`, {
        items: [
          { word: 'a', translation: 'A' },
          { word: 'b', translation: 'B' },
        ],
      })
    ).json().vocab;
    const login = async (email: string) => ({
      origin: ORIGIN,
      cookie: `wordflow_session=${sessionCookie(await register(app, { email }))}`,
    });
    const anna = await login('anna@example.org');
    const bob = await login('bob@example.org');

    expect((await req('POST', `/vocab/${a.id}/activate`, undefined, anna)).statusCode).toBe(200);
    expect(
      (await req('POST', `/packages/${pkg.id}/activate`, undefined, bob)).json(),
    ).toMatchObject({ activated: 2 });

    const annaView = (await req('GET', `/packages/${pkg.id}`, undefined, anna)).json();
    expect(annaView.package.counts).toMatchObject({ total: 2, inactive: 1, active: 1 });
    expect(annaView.vocab.map((v: { active: boolean }) => v.active)).toEqual([true, false]);
    const bobView = (await req('GET', `/packages/${pkg.id}`, undefined, bob)).json();
    expect(bobView.package.counts).toMatchObject({ total: 2, inactive: 0, active: 2 });
    // The superuser has activated nothing.
    expect((await req('GET', `/packages/${pkg.id}`)).json().package.counts.inactive).toBe(2);

    const off = await req('POST', `/vocab/${b.id}/deactivate`, undefined, bob);
    expect(off.json().vocab.active).toBe(false);
    expect(
      (await req('GET', `/packages/${pkg.id}`, undefined, bob)).json().package.counts.inactive,
    ).toBe(1);
  });
});
