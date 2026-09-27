import crypto from 'node:crypto';

/**
 * AES-256-GCM for API keys. The key is derived from APP_SECRET; stored
 * format: "v1:" + base64(iv | tag | ciphertext).
 */
export class SecretBox {
  private readonly key: Buffer;

  constructor(appSecret: string) {
    this.key = crypto.createHash('sha256').update(`gero-api-keys:${appSecret}`).digest();
  }

  encrypt(plain: string): string {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', this.key, iv);
    const data = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
    return `v1:${Buffer.concat([iv, cipher.getAuthTag(), data]).toString('base64')}`;
  }

  decrypt(stored: string): string {
    if (!stored.startsWith('v1:')) throw new Error('Unbekanntes Schlüsselformat');
    const raw = Buffer.from(stored.slice(3), 'base64');
    const decipher = crypto.createDecipheriv('aes-256-gcm', this.key, raw.subarray(0, 12));
    decipher.setAuthTag(raw.subarray(12, 28));
    return Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]).toString('utf8');
  }
}

/** "…abcd" – enough to recognise a key without revealing it. */
export function keyHint(key: string): string {
  return `…${key.slice(-4)}`;
}
