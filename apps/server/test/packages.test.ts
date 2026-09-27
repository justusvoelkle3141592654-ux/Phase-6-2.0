import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import { todayIn } from '@gero/shared';
import { ORIGIN, register, sessionCookie, testApp } from './helpers';

let app: FastifyInstance;
let headers: Record<string, string>;

beforeEach(async () => {
  app = await testApp();
  headers = { origin: ORIGIN, cookie: `gero_session=${sessionCookie(await register(app))}` };
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

  it('trennt die Daten der Accounts', async () => {
    const pkg = await createPackage();
    const [v] = (
      await req('POST', `/packages/${pkg.id}/vocab`, { items: [{ word: 'x', translation: 'y' }] })
    ).json().vocab;
    const bob = {
      origin: ORIGIN,
      cookie: `gero_session=${sessionCookie(await register(app, { email: 'bob@example.org' }))}`,
    };
    expect((await req('GET', '/packages', undefined, bob)).json().packages).toHaveLength(0);
    expect((await req('GET', `/packages/${pkg.id}`, undefined, bob)).statusCode).toBe(404);
    expect((await req('PATCH', `/vocab/${v.id}`, { word: 'z' }, bob)).statusCode).toBe(404);
    expect((await req('DELETE', `/packages/${pkg.id}`, undefined, bob)).statusCode).toBe(404);
    expect(
      (
        await req(
          'POST',
          `/packages/${pkg.id}/vocab`,
          { items: [{ word: 'a', translation: 'b' }] },
          bob,
        )
      ).statusCode,
    ).toBe(404);
  });
});
