import { describe, expect, it } from 'vitest';
import { applyAnswer } from '../src/stages';
import { addDays, todayIn } from '../src/dates';

const settings = { intervals: [5, 10, 20, 40, 80], wrongMode: 'reset' as const };
const today = '2026-09-27';

describe('Stufensystem', () => {
  it('richtig: eine Stufe höher, fällig nach dem Intervall der neuen Stufe', () => {
    expect(applyAnswer(1, true, settings, today)).toEqual({
      stage: 2,
      dueDate: '2026-10-02',
      learned: false,
    });
    expect(applyAnswer(2, true, settings, today)).toEqual({
      stage: 3,
      dueDate: '2026-10-07',
      learned: false,
    });
    expect(applyAnswer(3, true, settings, today).dueDate).toBe('2026-10-17');
    expect(applyAnswer(4, true, settings, today).dueDate).toBe('2026-11-06');
    expect(applyAnswer(5, true, settings, today)).toEqual({
      stage: 6,
      dueDate: '2026-12-16',
      learned: false,
    });
  });

  it('richtig auf Stufe 6: gelernt, nicht mehr fällig', () => {
    expect(applyAnswer(6, true, settings, today)).toEqual({
      stage: 6,
      dueDate: null,
      learned: true,
    });
  });

  it('falsch (Standard): zurück auf Stufe 1, sofort fällig', () => {
    expect(applyAnswer(5, false, settings, today)).toEqual({
      stage: 1,
      dueDate: today,
      learned: false,
    });
    expect(applyAnswer(1, false, settings, today)).toEqual({
      stage: 1,
      dueDate: today,
      learned: false,
    });
  });

  it('falsch mit „eine Stufe zurück“', () => {
    const back = { ...settings, wrongMode: 'back' as const };
    expect(applyAnswer(5, false, back, today)).toEqual({
      stage: 4,
      dueDate: today,
      learned: false,
    });
    expect(applyAnswer(1, false, back, today)).toEqual({
      stage: 1,
      dueDate: today,
      learned: false,
    });
  });

  it('nutzt eigene Intervalle', () => {
    expect(applyAnswer(1, true, { ...settings, intervals: [1, 2, 3, 4, 5] }, today).dueDate).toBe(
      '2026-09-28',
    );
  });
});

describe('Datum', () => {
  it('addiert Tage über Monats- und Jahresgrenzen', () => {
    expect(addDays('2026-12-30', 5)).toBe('2027-01-04');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });

  it('bestimmt "heute" in der Zeitzone des Accounts', () => {
    const lateEvening = new Date('2026-09-27T22:30:00Z');
    expect(todayIn('Europe/Berlin', lateEvening)).toBe('2026-09-28');
    expect(todayIn('America/New_York', lateEvening)).toBe('2026-09-27');
  });
});
