import { describe, expect, it } from 'vitest';
import { plannedReminders, reminderDate } from '../src/lib/reminders';

describe('Erinnerungen', () => {
  it('plant nur Tage mit fälligen Vokabeln, deren Uhrzeit noch kommt', () => {
    const now = new Date(2026, 8, 27, 19, 0);
    const days = [
      { day: '2026-09-27', count: 3 },
      { day: '2026-09-28', count: 0 },
      { day: '2026-09-29', count: 5 },
    ];
    expect(plannedReminders(days, '18:30', now)).toEqual([
      { id: 1002, at: reminderDate('2026-09-29', '18:30'), count: 5 },
    ]);
    expect(plannedReminders(days, '20:15', now).map((r) => r.id)).toEqual([1000, 1002]);
  });

  it('ohne Uhrzeit keine Erinnerungen', () => {
    expect(plannedReminders([{ day: '2026-09-27', count: 3 }], null)).toEqual([]);
  });

  it('nutzt die Uhrzeit des Geräts', () => {
    const d = reminderDate('2026-10-05', '07:45');
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([
      2026, 9, 5, 7, 45,
    ]);
  });
});
