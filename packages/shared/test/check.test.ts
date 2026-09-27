import { describe, expect, it } from 'vitest';
import {
  acceptedAnswers,
  allowedTypos,
  checkLocally,
  damerauLevenshtein,
  normalize,
} from '../src/check';

describe('normalize', () => {
  it('ignoriert Groß-/Kleinschreibung, Leerzeichen und Satzzeichen am Rand', () => {
    expect(normalize('  Das   Haus! ')).toBe('das haus');
    expect(normalize('„Haus“.')).toBe('haus');
    expect(normalize('...to go?')).toBe('to go');
  });

  it('vereinheitlicht Unicode (NFC)', () => {
    expect(normalize('Bär')).toBe('bär');
  });
});

describe('acceptedAnswers', () => {
  it('trennt Alternativen an Komma, Semikolon und Schrägstrich', () => {
    expect(acceptedAnswers('Haus, Gebäude; Heim/Zuhause')).toEqual([
      'haus',
      'gebäude',
      'heim',
      'zuhause',
    ]);
  });

  it('macht Teile in Klammern optional', () => {
    expect(acceptedAnswers('groß (bedeutend)')).toEqual(['groß bedeutend', 'groß']);
    expect(acceptedAnswers('(sich) leihen')).toEqual(['sich leihen', 'leihen']);
  });

  it('trennt nicht an Kommas innerhalb von Klammern', () => {
    expect(acceptedAnswers('loben (preisen, rühmen)')).toEqual(['loben preisen, rühmen', 'loben']);
  });
});

describe('damerauLevenshtein', () => {
  it('zählt Einfügen, Löschen, Ersetzen und Vertauschen als je einen Fehler', () => {
    expect(damerauLevenshtein('haus', 'haus')).toBe(0);
    expect(damerauLevenshtein('haus', 'hause')).toBe(1);
    expect(damerauLevenshtein('haus', 'has')).toBe(1);
    expect(damerauLevenshtein('haus', 'maus')).toBe(1);
    expect(damerauLevenshtein('haus', 'huas')).toBe(1);
    expect(damerauLevenshtein('freund', 'fruend')).toBe(1);
    expect(damerauLevenshtein('', 'abc')).toBe(3);
  });
});

describe('Tippfehler-Schwellen', () => {
  it('bis 4 Zeichen exakt, 5–8 Zeichen 1 Fehler, ab 9 Zeichen 2 Fehler', () => {
    expect([1, 2, 3, 4].map(allowedTypos)).toEqual([0, 0, 0, 0]);
    expect([5, 6, 7, 8].map(allowedTypos)).toEqual([1, 1, 1, 1]);
    expect([9, 12, 20].map(allowedTypos)).toEqual([2, 2, 2]);
  });
});

describe('checkLocally', () => {
  it('erkennt exakte Treffer', () => {
    expect(checkLocally('Haus', 'Haus; Gebäude').verdict).toBe('exact');
    expect(checkLocally(' gebäude. ', 'Haus; Gebäude').verdict).toBe('exact');
    expect(checkLocally('groß', 'groß (bedeutend)').verdict).toBe('exact');
    expect(checkLocally('groß bedeutend', 'groß (bedeutend)').verdict).toBe('exact');
  });

  it('akzeptiert kleine Tippfehler bei längeren Wörtern', () => {
    expect(checkLocally('Fruend', 'Freund')).toEqual({ verdict: 'typo', matched: 'freund' });
    expect(checkLocally('Gebaude', 'Haus; Gebäude').verdict).toBe('typo');
    expect(checkLocally('Schmeterlnig', 'Schmetterling').verdict).toBe('typo');
  });

  it('verlangt bei kurzen Wörtern exakte Schreibweise', () => {
    expect(checkLocally('Bar', 'Bär').verdict).toBe('unclear');
    expect(checkLocally('Hus', 'Haus').verdict).toBe('unclear');
  });

  it('lässt zu große Abweichungen offen (für die KI)', () => {
    expect(checkLocally('Freudn Kumpel', 'Freund').verdict).toBe('unclear');
    expect(checkLocally('Kumpel', 'Freund').verdict).toBe('unclear');
    expect(checkLocally('Frnd', 'Freund').verdict).toBe('unclear');
  });

  it('leere Antworten sind "empty"', () => {
    expect(checkLocally('', 'Haus').verdict).toBe('empty');
    expect(checkLocally('  ?! ', 'Haus').verdict).toBe('empty');
  });

  it('akzeptiert mehrere richtige Bedeutungen auf einmal', () => {
    expect(checkLocally('Haus, Gebäude', 'Haus; Gebäude; Heim').verdict).toBe('exact');
    expect(checkLocally('Haus / Gebaude', 'Haus; Gebäude').verdict).toBe('typo');
    expect(checkLocally('Haus, Auto', 'Haus; Gebäude').verdict).toBe('unclear');
  });
});
