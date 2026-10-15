import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { generateRegistrationCode, loadSecrets } from '../src/secrets';

const dirs: string[] = [];
function tempDir() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'wordflow-secrets-'));
  dirs.push(dir);
  return dir;
}
afterEach(() => {
  for (const dir of dirs.splice(0)) fs.rmSync(dir, { recursive: true, force: true });
});

describe('Schlüssel und Registrierungscode', () => {
  it('erzeugt fehlende Werte und speichert sie mit Rechten 600', () => {
    const dir = tempDir();
    const first = loadSecrets(dir, { registrationCode: null, appSecret: null });
    expect(first.generated).toEqual(['registrationCode', 'appSecret']);
    expect(first.registrationCode).toMatch(/^[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);
    expect(Buffer.from(first.appSecret, 'base64')).toHaveLength(32);

    const file = path.join(dir, 'secrets.json');
    expect(fs.statSync(file).mode & 0o777).toBe(0o600);

    const second = loadSecrets(dir, { registrationCode: null, appSecret: null });
    expect(second.generated).toEqual([]);
    expect(second.registrationCode).toBe(first.registrationCode);
    expect(second.appSecret).toBe(first.appSecret);
  });

  it('Werte aus der Umgebung haben Vorrang und werden nicht gespeichert', () => {
    const dir = tempDir();
    const s = loadSecrets(dir, { registrationCode: 'ENV-CODE', appSecret: 'env-secret' });
    expect(s).toEqual({ registrationCode: 'ENV-CODE', appSecret: 'env-secret', generated: [] });
    expect(fs.existsSync(path.join(dir, 'secrets.json'))).toBe(false);
  });

  it('Codes enthalten keine verwechselbaren Zeichen', () => {
    for (let i = 0; i < 50; i++) expect(generateRegistrationCode()).not.toMatch(/[01OIL]/);
  });
});
