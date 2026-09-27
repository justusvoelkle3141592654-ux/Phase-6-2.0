import { hash, verify } from '@node-rs/argon2';

// Argon2id with the OWASP minimum: 19 MiB memory, 2 iterations, 1 lane.
const OPTIONS = { memoryCost: 19456, timeCost: 2, parallelism: 1 } as const;

export function hashPassword(password: string): Promise<string> {
  return hash(password, OPTIONS);
}

export async function verifyPassword(passwordHash: string, password: string): Promise<boolean> {
  try {
    return await verify(passwordHash, password);
  } catch {
    return false;
  }
}

/** Used when the e-mail is unknown, so the response time does not reveal it. */
let dummyHash: Promise<string> | null = null;
export async function burnPasswordCheck(password: string): Promise<void> {
  dummyHash ??= hashPassword('gero-dummy-password');
  await verifyPassword(await dummyHash, password);
}
