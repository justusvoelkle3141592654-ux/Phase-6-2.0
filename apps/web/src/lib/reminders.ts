import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LocalNotifications } from '@capacitor/local-notifications';
import type { DueDayDto } from '@gero/shared';
import { useI18n } from '../i18n';
import { api } from './api';
import { fill } from './format';
import { isApp } from './platform';
import { useSettings } from './settings';

/** Notification ids 1000–1029: one per day of the next 30 days. */
const FIRST_ID = 1000;
const DAYS = 30;

/** Date at `time` ("HH:MM", device time) on calendar day `day` ("YYYY-MM-DD"). */
export function reminderDate(day: string, time: string): Date {
  const [y, mo, d] = day.split('-').map(Number) as [number, number, number];
  const [h, mi] = time.split(':').map(Number) as [number, number];
  return new Date(y, mo - 1, d, h, mi, 0, 0);
}

/** Which reminders to schedule: days with due words whose reminder time is still ahead. */
export function plannedReminders(days: DueDayDto[], time: string | null, now = new Date()) {
  if (!time) return [];
  return days
    .map((d, i) => ({ id: FIRST_ID + i, at: reminderDate(d.day, time), count: d.count }))
    .filter((r) => r.count > 0 && r.at.getTime() > now.getTime());
}

export async function notificationsAllowed(request: boolean): Promise<boolean> {
  if (!isApp) return false;
  const status = request
    ? await LocalNotifications.requestPermissions()
    : await LocalNotifications.checkPermissions();
  return status.display === 'granted';
}

/**
 * Android app: keeps local notifications in line with the reminder time and
 * the due words of the next 30 days. Re-planned whenever the data changes.
 */
export function useReminderSync(enabled: boolean) {
  const { m } = useI18n();
  const active = isApp && enabled;
  const settings = useSettings(active);
  const days = useQuery({
    queryKey: ['overview', 'due-days'],
    queryFn: async () => (await api<{ days: DueDayDto[] }>('/overview/due-days')).days,
    enabled: active,
  });
  const time = settings.data?.reminderTime ?? null;
  const data = days.data;

  useEffect(() => {
    if (!active || !settings.data || !data) return;
    void (async () => {
      await LocalNotifications.cancel({
        notifications: Array.from({ length: DAYS }, (_, i) => ({ id: FIRST_ID + i })),
      });
      const planned = plannedReminders(data, time);
      if (planned.length === 0 || !(await notificationsAllowed(false))) return;
      await LocalNotifications.schedule({
        notifications: planned.map((r) => ({
          id: r.id,
          title: 'Gero',
          body:
            r.count === 1
              ? m.app.notificationBodyOne
              : fill(m.app.notificationBody, { n: r.count }),
          schedule: { at: r.at, allowWhileIdle: true },
          smallIcon: 'ic_stat_gero',
        })),
      });
    })().catch(() => undefined);
  }, [active, settings.data, data, time, m]);
}
