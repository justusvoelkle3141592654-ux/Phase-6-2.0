#!/usr/bin/env node
// Updates WordFlow from GitHub without git: downloads the branch as a tarball and
// copies it over the project. Started by the superuser's update button
// (POST /api/admin/update) as its own process, because the dev server reloads
// while files are being replaced.
//
// Usage: node scripts/update.mjs --repo owner/name --branch master --root <project> --data <data dir>

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const repo = arg('repo', 'rcode125/Phase-6-2.0');
const branch = arg('branch', 'master');
const root = path.resolve(arg('root', path.join(import.meta.dirname, '..')));
const dataDir = path.resolve(arg('data', path.join(root, 'data')));
const lockFile = path.join(dataDir, 'update.lock');
const stateFile = path.join(dataDir, 'update.json');

/** Never touched by an update (paths relative to the project root). */
const KEEP = ['data', '.env', 'node_modules', '.git', 'release', 'apps/web/android', '.claude'];
const keep = (rel) =>
  rel.split(path.sep).includes('node_modules') ||
  KEEP.some((k) => rel === k || rel.startsWith(k + path.sep)) ||
  rel.startsWith('.env.') ||
  path.resolve(root, rel).startsWith(dataDir);

const log = (...parts) => console.log(new Date().toISOString(), ...parts);

function run(cmd, args, cwd) {
  log('$', cmd, ...args);
  const res = spawnSync(cmd, args, { cwd, stdio: 'inherit', shell: process.platform === 'win32' });
  if (res.status !== 0)
    throw new Error(`${cmd} ${args.join(' ')} ist fehlgeschlagen (${res.status})`);
}

function read(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

const headers = { 'User-Agent': 'WordFlow-Updater' };
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-update-'));

try {
  log(`Update von ${repo}@${branch} nach ${root}`);

  const commit = await fetch(`https://api.github.com/repos/${repo}/commits/${branch}`, {
    headers: { ...headers, Accept: 'application/vnd.github+json' },
  });
  if (!commit.ok) throw new Error(`GitHub: HTTP ${commit.status}`);
  const { sha } = await commit.json();
  log('Neuester Commit:', sha);

  const archive = await fetch(`https://codeload.github.com/${repo}/tar.gz/${sha}`, { headers });
  if (!archive.ok) throw new Error(`Download: HTTP ${archive.status}`);
  const tarFile = path.join(tmp, 'source.tar.gz');
  fs.writeFileSync(tarFile, Buffer.from(await archive.arrayBuffer()));
  log('Heruntergeladen:', fs.statSync(tarFile).size, 'Bytes');

  const extractDir = path.join(tmp, 'src');
  fs.mkdirSync(extractDir);
  run('tar', ['-xzf', tarFile, '-C', extractDir], tmp);
  const [top] = fs.readdirSync(extractDir);
  if (!top) throw new Error('Archiv ist leer');
  const source = path.join(extractDir, top);
  if (!fs.existsSync(path.join(source, 'package.json'))) throw new Error('Kein WordFlow-Archiv');

  // Dependencies first, so the reloading server finds every new package.
  const manifests = [
    'package.json',
    'package-lock.json',
    'apps/server/package.json',
    'apps/web/package.json',
    'packages/shared/package.json',
  ];
  const changed = manifests.filter(
    (f) =>
      read(path.join(source, f)) !== null &&
      read(path.join(source, f)) !== read(path.join(root, f)),
  );
  if (changed.length > 0) {
    log('Geänderte Abhängigkeiten:', changed.join(', '));
    for (const f of changed) {
      fs.mkdirSync(path.dirname(path.join(root, f)), { recursive: true });
      fs.copyFileSync(path.join(source, f), path.join(root, f));
    }
    run('npm', ['install', '--no-audit', '--no-fund'], root);
  }

  log('Kopiere Dateien …');
  let copied = 0;
  fs.cpSync(source, root, {
    recursive: true,
    force: true,
    filter: (src) => {
      const rel = path.relative(source, src);
      if (rel === '') return true;
      if (keep(rel)) return false;
      if (fs.statSync(src).isFile()) copied++;
      return true;
    },
  });
  log(`${copied} Dateien aktualisiert`);

  if (process.env.NODE_ENV === 'production') {
    run('npm', ['run', 'build'], root);
    log('Build fertig. Bitte den Server neu starten.');
  }

  fs.mkdirSync(dataDir, { recursive: true });
  fs.writeFileSync(stateFile, JSON.stringify({ sha, updatedAt: new Date().toISOString() }) + '\n');
  log('Update fertig.');
} catch (err) {
  log('FEHLER:', err instanceof Error ? err.message : String(err));
  process.exitCode = 1;
} finally {
  fs.rmSync(tmp, { recursive: true, force: true });
  fs.rmSync(lockFile, { force: true });
}
