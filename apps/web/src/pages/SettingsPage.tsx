import { useState, type FormEvent } from 'react';
import { LogOut } from 'lucide-react';
import { PASSWORD_MIN_LENGTH, type UserDto, type UserSettings, type WrongMode } from '@gero/shared';
import { LanguageSwitch } from '../components/LanguageSwitch';
import { Notice } from '../components/Notice';
import { AiSettings } from '../components/ai/AiSettings';
import {
  IntervalsField,
  parseIntervals,
  parseTimeout,
  TimeoutField,
  TimezoneSelect,
  WrongModeField,
} from '../components/SettingsFields';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { useChangePassword, useLogout, useMe, useUpdateProfile } from '../lib/auth';
import { errorMessage } from '../lib/errors';
import { useSettings, useUpdateSettings } from '../lib/settings';
import { isApp } from '../lib/platform';
import { notificationsAllowed } from '../lib/reminders';

function AccountSection({ user }: { user: UserDto }) {
  const { m } = useI18n();
  const [name, setName] = useState(user.name);
  const [timezone, setTimezone] = useState(user.timezone);
  const update = useUpdateProfile();
  const logout = useLogout();

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    update.mutate({ name, timezone });
  };

  return (
    <section className="panel space-y-4" aria-labelledby="account-title">
      <h2 id="account-title" className="text-xl font-bold">
        {m.account.title}
      </h2>
      <p className="text-ink-soft">
        {m.account.signedInAs} <strong className="text-ink">{user.email}</strong>
      </p>
      <form className="space-y-4" onSubmit={onSubmit}>
        <div>
          <label className="label" htmlFor="account-name">
            {m.account.name}
          </label>
          <input
            id="account-name"
            className="field"
            autoComplete="nickname"
            maxLength={80}
            value={name}
            onChange={(e) => {
              update.reset();
              setName(e.target.value);
            }}
          />
        </div>
        <div>
          <label className="label" htmlFor="account-timezone">
            {m.fields.timezone}
          </label>
          <TimezoneSelect
            id="account-timezone"
            value={timezone}
            onChange={(v) => {
              update.reset();
              setTimezone(v);
            }}
          />
        </div>
        <button type="submit" className="btn btn-secondary" disabled={update.isPending}>
          {m.account.save}
        </button>
      </form>
      {update.isSuccess && <Notice tone="ok">{m.account.saved}</Notice>}
      {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
      <button
        type="button"
        className="btn btn-secondary"
        onClick={() => logout.mutate()}
        disabled={logout.isPending}
      >
        <LogOut className="size-4.5" aria-hidden="true" />
        {m.account.logout}
      </button>
      {logout.isError && <Notice tone="error">{errorMessage(m, logout.error)}</Notice>}
    </section>
  );
}

function LearningSection({ settings }: { settings: UserSettings }) {
  const { m } = useI18n();
  const [intervals, setIntervals] = useState(settings.intervals.map(String));
  const [wrongMode, setWrongMode] = useState<WrongMode>(settings.wrongMode);
  const [timeout, setTimeoutValue] = useState(String(settings.aiTimeoutMs / 1000));
  const update = useUpdateSettings();
  const parsedIntervals = parseIntervals(intervals);
  const timeoutMs = parseTimeout(timeout);
  const valid = parsedIntervals !== null && timeoutMs !== null;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!valid) return;
    update.mutate({ intervals: parsedIntervals, wrongMode, aiTimeoutMs: timeoutMs });
  };
  const edit =
    <T,>(setter: (value: T) => void) =>
    (value: T) => {
      update.reset();
      setter(value);
    };

  return (
    <section className="panel" aria-labelledby="learning-title">
      <h2 id="learning-title" className="mb-4 text-xl font-bold">
        {m.settings.learningTitle}
      </h2>
      <form className="space-y-6" onSubmit={onSubmit}>
        <IntervalsField value={intervals} onChange={edit(setIntervals)} />
        <WrongModeField value={wrongMode} onChange={edit(setWrongMode)} />
        <TimeoutField value={timeout} onChange={edit(setTimeoutValue)} />
        {update.isSuccess && <Notice tone="ok">{m.account.saved}</Notice>}
        {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
        <button type="submit" className="btn btn-primary" disabled={update.isPending || !valid}>
          {m.account.save}
        </button>
      </form>
    </section>
  );
}

function ReminderSection({ settings }: { settings: UserSettings }) {
  const { m } = useI18n();
  const [time, setTime] = useState(settings.reminderTime ?? '18:00');
  const [denied, setDenied] = useState(false);
  const update = useUpdateSettings();
  const on = settings.reminderTime !== null;

  const save = async (value: string | null) => {
    setDenied(false);
    if (value && isApp && !(await notificationsAllowed(true))) setDenied(true);
    update.mutate({ reminderTime: value });
  };

  return (
    <section className="panel space-y-4" aria-labelledby="reminder-title">
      <div>
        <h2 id="reminder-title" className="text-xl font-bold">
          {m.app.reminder}
        </h2>
        <p className="mt-1 text-sm text-ink-soft">
          {m.app.reminderHint} {!isApp && m.app.reminderAppOnly}
        </p>
      </div>
      <div role="radiogroup" aria-label={m.app.reminder} className="segmented">
        <button type="button" role="radio" aria-checked={!on} onClick={() => void save(null)}>
          {m.app.reminderOff}
        </button>
        <button type="button" role="radio" aria-checked={on} onClick={() => void save(time)}>
          {m.app.reminderOn}
        </button>
      </div>
      {on && (
        <div className="flex items-center gap-3">
          <label className="sr-only" htmlFor="reminder-time">
            {m.app.reminderOn}
          </label>
          <input
            id="reminder-time"
            type="time"
            className="field w-36 tabular-nums"
            value={time}
            onChange={(e) => setTime(e.target.value)}
          />
          <button
            type="button"
            className="btn btn-secondary"
            disabled={
              !/^\d{2}:\d{2}$/.test(time) || time === settings.reminderTime || update.isPending
            }
            onClick={() => void save(time)}
          >
            {m.account.save}
          </button>
        </div>
      )}
      {denied && <Notice tone="error">{m.app.reminderDenied}</Notice>}
      {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
    </section>
  );
}

function PasswordSection() {
  const { m } = useI18n();
  const [currentPassword, setCurrent] = useState('');
  const [newPassword, setNew] = useState('');
  const change = useChangePassword();

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    change.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          setCurrent('');
          setNew('');
        },
      },
    );
  };

  return (
    <section className="panel" aria-labelledby="password-title">
      <h2 id="password-title" className="mb-4 text-xl font-bold">
        {m.account.changePassword}
      </h2>
      <form className="space-y-4" onSubmit={onSubmit}>
        <div>
          <label className="label" htmlFor="pw-current">
            {m.account.currentPassword}
          </label>
          <input
            id="pw-current"
            className="field"
            type="password"
            autoComplete="current-password"
            required
            value={currentPassword}
            onChange={(e) => setCurrent(e.target.value)}
          />
        </div>
        <div>
          <label className="label" htmlFor="pw-new">
            {m.account.newPassword}
          </label>
          <input
            id="pw-new"
            className="field"
            type="password"
            autoComplete="new-password"
            minLength={PASSWORD_MIN_LENGTH}
            required
            value={newPassword}
            onChange={(e) => setNew(e.target.value)}
            aria-describedby="pw-new-hint"
          />
          <p id="pw-new-hint" className="hint">
            {m.auth.passwordHint}
          </p>
        </div>
        {change.isSuccess && <Notice tone="ok">{m.account.passwordChanged}</Notice>}
        {change.isError && <Notice tone="error">{errorMessage(m, change.error)}</Notice>}
        <button type="submit" className="btn btn-primary" disabled={change.isPending}>
          {m.account.changePassword}
        </button>
      </form>
    </section>
  );
}

export function SettingsPage() {
  const { m } = useI18n();
  const update = useUpdateProfile();
  // Null for one render right after signing out, before AuthGate takes over.
  const user = useMe().data;
  const settings = useSettings(Boolean(user));
  if (!user) return null;
  return (
    <>
      <PageHeader title={m.settings.title} />
      <div className="grid max-w-xl grid-cols-[minmax(0,1fr)] gap-6">
        <section className="panel">
          <span className="label">{m.settings.language}</span>
          <LanguageSwitch
            label={m.settings.language}
            onChange={(uiLanguage) => update.mutate({ uiLanguage })}
          />
        </section>
        <AccountSection user={user} />
        {settings.data && <LearningSection settings={settings.data} />}
        {settings.data && <ReminderSection settings={settings.data} />}
        <AiSettings />
        <PasswordSection />
      </div>
    </>
  );
}
