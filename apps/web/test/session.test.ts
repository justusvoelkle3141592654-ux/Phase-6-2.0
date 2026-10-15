import { describe, expect, it } from 'vitest';
import type { AnswerResult, LearnCard } from '@wordflow/shared';
import {
  changeDirection,
  overrideLast,
  recordAnswer,
  resolveDirection,
  sessionStats,
  startSession,
} from '../src/lib/session';

const card = (
  id: number,
  packageDirection: LearnCard['packageDirection'] = 'foreign_native',
): LearnCard => ({
  vocabId: id,
  packageId: 1,
  packageName: 'P',
  language: 'en',
  packageDirection,
  word: `w${id}`,
  extra: '',
  translation: `t${id}`,
  stage: 1,
});

const result = (correct: boolean, extra: Partial<AnswerResult> = {}): AnswerResult => ({
  attemptId: 1,
  correct,
  decidedBy: 'self',
  stageBefore: 1,
  stageAfter: correct ? 2 : 1,
  learned: false,
  canOverride: false,
  ...extra,
});

describe('Lernsitzung', () => {
  it('nutzt die Richtung des Pakets oder die gewählte Richtung', () => {
    expect(resolveDirection(card(1, 'native_foreign'), 'package')).toBe('native_foreign');
    expect(resolveDirection(card(1, 'native_foreign'), 'foreign_native')).toBe('foreign_native');
    expect(resolveDirection(card(1, 'random'), 'package', () => 0.2)).toBe('foreign_native');
    expect(resolveDirection(card(1, 'random'), 'package', () => 0.8)).toBe('native_foreign');
  });

  it('hängt falsche Vokabeln einmal ans Ende', () => {
    let s = startSession([card(1), card(2)], 'package');
    s = recordAnswer(s, result(false));
    expect(s.items.map((i) => [i.card.vocabId, i.repeat])).toEqual([
      [1, false],
      [2, false],
      [1, true],
    ]);
    s = recordAnswer(s, result(true));
    s = recordAnswer(s, result(false)); // wrong again in the repetition: not repeated again
    expect(s.index).toBe(s.items.length);
    expect(sessionStats(s)).toEqual({ correct: 1, wrong: 1, repeatedCorrect: 0, learned: 0 });
  });

  it('„Ich hatte recht“ entfernt die geplante Wiederholung', () => {
    let s = startSession([card(1), card(2)], 'package');
    s = recordAnswer(s, result(false, { canOverride: true }));
    s = overrideLast(s, result(true, { decidedBy: 'correction' }));
    expect(s.items).toHaveLength(2);
    expect(s.results[0]!.result.correct).toBe(true);
  });

  it('ändert die Richtung nur für kommende Karten', () => {
    let s = startSession([card(1), card(2), card(3)], 'package');
    s = recordAnswer(s, result(true));
    s = changeDirection(s, 'native_foreign');
    expect(s.items.map((i) => i.direction)).toEqual([
      'foreign_native',
      'foreign_native',
      'native_foreign',
    ]);
  });
});
