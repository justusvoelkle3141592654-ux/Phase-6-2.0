import { describe, expect, it } from 'vitest';
import { de } from '../src/i18n/de';
import { en } from '../src/i18n/en';

function keys(obj: object, prefix = ''): string[] {
  return Object.entries(obj).flatMap(([k, v]) =>
    v && typeof v === 'object' ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`],
  );
}

describe('Übersetzungen', () => {
  it('Englisch hat dieselben Schlüssel wie Deutsch', () => {
    expect(keys(en).sort()).toEqual(keys(de).sort());
  });

  it('keine leeren Texte', () => {
    for (const catalog of [de, en]) {
      for (const key of keys(catalog)) {
        const value = key
          .split('.')
          .reduce<unknown>((o, k) => (o as Record<string, unknown>)[k], catalog);
        if (typeof value === 'string') expect(value.trim(), key).not.toBe('');
      }
    }
  });
});
