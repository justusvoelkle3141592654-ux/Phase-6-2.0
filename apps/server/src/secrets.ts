import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export interface Secrets {
  registrationCode: string;
  appSecret: string;
  /** Names of the values that were generated on this start. */
  generated: Array<'registrationCode' | 'appSecret'>;
}

/** Letters and digits that cannot be confused (no 0/O, 1/I/L). */
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

export function generateRegistrationCode(): string {
  const chars = Array.from(
    { length: 12 },
    () => CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)],
  );
  return [0, 4, 8].map((i) => chars.slice(i, i + 4).join('')).join('-');
}

/**
 * Values from the environment win. Missing values are read from
 * `<dataDir>/secrets.json`, or generated and written there (mode 600).
 */
export function loadSecrets(
  dataDir: string,
  fromEnv: { registrationCode: string | null; appSecret: string | null },
): Secrets {
  const file = path.join(dataDir, 'secrets.json');
  let stored: Partial<Record<'registrationCode' | 'appSecret', string>> = {};
  if (fs.existsSync(file)) {
    stored = JSON.parse(fs.readFileSync(file, 'utf8'));
  }

  const generated: Secrets['generated'] = [];
  const registrationCode =
    fromEnv.registrationCode ??
    stored.registrationCode ??
    (generated.push('registrationCode'), generateRegistrationCode());
  const appSecret =
    fromEnv.appSecret ??
    stored.appSecret ??
    (generated.push('appSecret'), crypto.randomBytes(32).toString('base64'));

  if (generated.length > 0) {
    fs.mkdirSync(dataDir, { recursive: true });
    const next = { ...stored };
    if (generated.includes('registrationCode')) next.registrationCode = registrationCode;
    if (generated.includes('appSecret')) next.appSecret = appSecret;
    fs.writeFileSync(file, JSON.stringify(next, null, 2) + '\n', { mode: 0o600 });
    fs.chmodSync(file, 0o600);
  }

  return { registrationCode, appSecret, generated };
}
