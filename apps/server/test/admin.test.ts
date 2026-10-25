import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyInstance } from 'fastify';
import AdmZip from 'adm-zip';
import { adminHeaders, ORIGIN, register, sessionCookie, testApp } from './helpers';

let app: FastifyInstance;
let headers: Record<string, string>;

beforeEach(async () => {
  app = await testApp();
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

function login(email: string, password: string) {
  return app.inject({
    method: 'POST',
    url: '/api/auth/login',
    headers: { origin: ORIGIN },
    payload: { email, password },
  });
}

describe('Accountverwaltung', () => {
  it('legt Accounts an, ändert, setzt Passwörter und löscht', async () => {
    const created = await req('POST', '/admin/users', {
      email: 'Anna@Example.org',
      name: 'Anna',
      password: 'geheim123',
    });
    expect(created.statusCode).toBe(201);
    const anna = created.json().user;
    expect(anna).toMatchObject({ email: 'anna@example.org', name: 'Anna', role: 'user' });

    const list = (await req('GET', '/admin/users')).json().users;
    expect(list.map((u: { email: string }) => u.email)).toEqual([
      'admin@wordflow.com',
      'anna@example.org',
    ]);

    const session = sessionCookie(await login('anna@example.org', 'geheim123'))!;
    const patched = await req('PATCH', `/admin/users/${anna.id}`, {
      name: 'Anna B.',
      password: 'neuesPasswort',
    });
    expect(patched.json().user.name).toBe('Anna B.');
    // A new password signs the account out.
    const me = await app.inject({
      method: 'GET',
      url: '/api/auth/me',
      headers: { cookie: `wordflow_session=${session}` },
    });
    expect(me.statusCode).toBe(401);
    expect((await login('anna@example.org', 'neuesPasswort')).statusCode).toBe(200);

    expect((await req('DELETE', `/admin/users/${anna.id}`)).statusCode).toBe(204);
    expect((await login('anna@example.org', 'neuesPasswort')).statusCode).toBe(401);
  });

  it('lehnt doppelte E-Mails ab und schützt den Superuser', async () => {
    await req('POST', '/admin/users', { email: 'a@example.org', password: 'geheim123' });
    const dup = await req('POST', '/admin/users', {
      email: 'A@example.org',
      password: 'geheim123',
    });
    expect(dup.statusCode).toBe(409);
    const admin = (await req('GET', '/admin/users')).json().users[0];
    expect((await req('DELETE', `/admin/users/${admin.id}`)).statusCode).toBe(403);
  });

  it('normale Accounts haben keinen Zugriff', async () => {
    const bob = {
      origin: ORIGIN,
      cookie: `wordflow_session=${sessionCookie(await register(app, { email: 'bob@example.org' }))}`,
    };
    expect((await req('GET', '/admin/users', undefined, bob)).statusCode).toBe(403);
    expect((await req('GET', '/admin/backup', undefined, bob)).statusCode).toBe(403);
    expect((await req('GET', '/admin/update', undefined, bob)).statusCode).toBe(403);
    expect((await req('POST', '/admin/update', undefined, bob)).statusCode).toBe(403);
    expect((await req('GET', '/admin/users', undefined, { origin: ORIGIN })).statusCode).toBe(401);
  });
});

describe('Datenbank sichern', () => {
  it('exportiert ZIP und stellt daraus wieder her', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-restore-'));
    const fileApp = await testApp({ dataDir: dir, dbFile: path.join(dir, 'wordflow.db') });
    try {
      const h = await adminHeaders(fileApp);
      const call = (method: 'GET' | 'POST', url: string, payload?: object) =>
        fileApp.inject({ method, url: `/api${url}`, headers: h, payload });

      await call('POST', '/packages', { name: 'Vorher', language: 'en' });
      fs.mkdirSync(path.join(dir, 'uploads', '1'), { recursive: true });
      fs.writeFileSync(path.join(dir, 'uploads', '1', 'foto.jpg'), 'bild');
      fs.writeFileSync(path.join(dir, 'secrets.json'), '{"appSecret":"test-secret"}');

      const backup = await call('GET', '/admin/backup');
      expect(backup.statusCode).toBe(200);
      expect(backup.headers['content-type']).toBe('application/zip');
      expect(backup.headers['content-disposition']).toMatch(/wordflow-backup-.*\.zip/);
      const zip = new AdmZip(backup.rawPayload);
      const names = zip.getEntries().map((e) => e.entryName);
      expect(names).toEqual(
        expect.arrayContaining(['wordflow.db', 'uploads/1/foto.jpg', 'secrets.json']),
      );

      // Change everything, then restore.
      await call('POST', '/packages', { name: 'Nachher', language: 'en' });
      fs.rmSync(path.join(dir, 'uploads'), { recursive: true });
      fs.writeFileSync(path.join(dir, 'secrets.json'), '{}');

      const form = new FormData();
      form.append('backup', new Blob([backup.rawPayload]), 'backup.zip');
      const request = new Request('http://x', { method: 'POST', body: form });
      const restored = await fileApp.inject({
        method: 'POST',
        url: '/api/admin/restore',
        headers: { ...h, 'content-type': request.headers.get('content-type')! },
        payload: Buffer.from(await request.arrayBuffer()),
      });
      expect(restored.statusCode).toBe(204);

      const h2 = { ...h };
      const pkgs = (
        await fileApp.inject({ method: 'GET', url: '/api/packages', headers: h2 })
      ).json().packages;
      expect(pkgs.map((p: { name: string }) => p.name)).toEqual(['Vorher']);
      expect(fs.readFileSync(path.join(dir, 'uploads', '1', 'foto.jpg'), 'utf8')).toBe('bild');
      expect(JSON.parse(fs.readFileSync(path.join(dir, 'secrets.json'), 'utf8'))).toEqual({
        appSecret: 'test-secret',
      });
    } finally {
      await fileApp.close();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });

  it('lehnt ungültige Backups ab', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-restore-'));
    const fileApp = await testApp({ dataDir: dir, dbFile: path.join(dir, 'wordflow.db') });
    try {
      const h = await adminHeaders(fileApp);
      const send = async (data: Buffer) => {
        const form = new FormData();
        form.append('backup', new Blob([data]), 'backup.zip');
        const request = new Request('http://x', { method: 'POST', body: form });
        return fileApp.inject({
          method: 'POST',
          url: '/api/admin/restore',
          headers: { ...h, 'content-type': request.headers.get('content-type')! },
          payload: Buffer.from(await request.arrayBuffer()),
        });
      };
      expect((await send(Buffer.from('kein zip'))).statusCode).toBe(400);
      const noDb = new AdmZip();
      noDb.addFile('foo.txt', Buffer.from('x'));
      expect((await send(noDb.toBuffer())).statusCode).toBe(400);
      const notSqlite = new AdmZip();
      notSqlite.addFile('wordflow.db', Buffer.from('keine datenbank'));
      expect((await send(notSqlite.toBuffer())).statusCode).toBe(400);
    } finally {
      await fileApp.close();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});

describe('Update', () => {
  it('vergleicht den installierten mit dem neuesten Commit auf GitHub', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-update-'));
    const calls: string[] = [];
    const auth: (string | undefined)[] = [];
    const fetchImpl = (async (url: string, init?: RequestInit) => {
      calls.push(String(url));
      auth.push((init?.headers as Record<string, string> | undefined)?.Authorization);
      return new Response(JSON.stringify({ sha: 'abc123' }), { status: 200 });
    }) as typeof fetch;
    const { buildApp } = await import('../src/app');
    const { loadConfig } = await import('../src/config');
    const updApp = await buildApp({
      ...loadConfig({}),
      logLevel: 'silent',
      webDist: null,
      dataDir: dir,
      dbFile: ':memory:',
      appSecret: 'x',
      githubToken: 'github_pat_test',
      updateFetch: fetchImpl,
    });
    try {
      const h = await adminHeaders(updApp);
      const first = (
        await updApp.inject({ method: 'GET', url: '/api/admin/update', headers: h })
      ).json();
      expect(first).toMatchObject({
        current: null,
        latest: 'abc123',
        available: true,
        running: false,
      });
      expect(calls[0]).toBe(
        `https://api.github.com/repos/rcode125/Phase-6-2.0/commits/claude/new-session-uq36ca`,
      );
      expect(auth[0]).toBe('Bearer github_pat_test');

      fs.writeFileSync(path.join(dir, 'update.json'), JSON.stringify({ sha: 'abc123' }));
      const second = (
        await updApp.inject({ method: 'GET', url: '/api/admin/update', headers: h })
      ).json();
      expect(second).toMatchObject({ current: 'abc123', available: false });
    } finally {
      await updApp.close();
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});
