import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import {
  PASSWORD_MIN_LENGTH,
  type UserDto,
  type UserSettings,
  type WrongMode,
} from '@wordflow/shared';
import { LanguageSwitch } from '../components/LanguageSwitch';
import { Notice } from '../components/Notice';
import { AiSettings } from '../components/ai/AiSettings';
import { AccountsAdmin } from '../components/admin/AccountsAdmin';
import { BackupAdmin } from '../components/admin/BackupAdmin';
import { UpdateAdmin } from '../components/admin/UpdateAdmin';
import {
  AutoNextFields,
  DailyLimitField,
  IntervalsField,
  parseDailyLimit,
  parseIntervals,
  parseTimeout,
  TimeoutField,
  TimezoneSelect,
  WrongModeField,
} from '../components/SettingsFields';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { useChangePassword, useDeleteAccount, useMe, useUpdateProfile } from '../lib/auth';
import { errorMessage } from '../lib/errors';
import { useSettings, useUpdateSettings } from '../lib/settings';

function AccountSection({ user }: { user: UserDto }) {
  const { m } = useI18n();
  const [name, setName] = useState(user.name);
  const [timezone, setTimezone] = useState(user.timezone);
  const update = useUpdateProfile();

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
    </section>
  );
}

function LearningSection({ settings }: { settings: UserSettings }) {
  const { m } = useI18n();
  const [intervals, setIntervals] = useState(settings.intervals.map(String));
  const [wrongMode, setWrongMode] = useState<WrongMode>(settings.wrongMode);
  const [dailyLimit, setDailyLimit] = useState(String(settings.dailyLimit));
  const [autoNext, setAutoNext] = useState({
    autoNextCorrectMs: settings.autoNextCorrectMs,
    autoNextTypoMs: settings.autoNextTypoMs,
    autoNextWrongMs: settings.autoNextWrongMs,
  });
  const update = useUpdateSettings();
  const parsedIntervals = parseIntervals(intervals);
  const parsedLimit = parseDailyLimit(dailyLimit);
  const valid = parsedIntervals !== null && parsedLimit !== null;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!valid) return;
    update.mutate({ intervals: parsedIntervals, wrongMode, dailyLimit: parsedLimit, ...autoNext });
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
        <DailyLimitField value={dailyLimit} onChange={edit(setDailyLimit)} />
        <IntervalsField value={intervals} onChange={edit(setIntervals)} />
        <WrongModeField value={wrongMode} onChange={edit(setWrongMode)} />
        <AutoNextFields value={autoNext} onChange={edit(setAutoNext)} />
        {update.isSuccess && <Notice tone="ok">{m.account.saved}</Notice>}
        {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
        <button type="submit" className="btn btn-primary" disabled={update.isPending || !valid}>
          {m.account.save}
        </button>
      </form>
    </section>
  );
}

/** Superuser: time limit of the AI answer check (part of the AI setup for everyone). */
function AiTimeoutSection({ settings }: { settings: UserSettings }) {
  const { m } = useI18n();
  const [timeout, setTimeoutValue] = useState(String(settings.aiTimeoutMs / 1000));
  const update = useUpdateSettings();
  const timeoutMs = parseTimeout(timeout);

  return (
    <section className="panel" aria-labelledby="timeout-title">
      <h2 id="timeout-title" className="mb-4 text-xl font-bold">
        {m.fields.timeout}
      </h2>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          if (timeoutMs !== null) update.mutate({ aiTimeoutMs: timeoutMs });
        }}
      >
        <TimeoutField
          value={timeout}
          onChange={(v) => {
            update.reset();
            setTimeoutValue(v);
          }}
        />
        {update.isSuccess && <Notice tone="ok">{m.account.saved}</Notice>}
        {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
        <button
          type="submit"
          className="btn btn-primary"
          disabled={update.isPending || timeoutMs === null}
        >
          {m.account.save}
        </button>
      </form>
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

function DeleteAccountSection() {
  const { m } = useI18n();
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const remove = useDeleteAccount();

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    remove.mutate({ password });
  };

  const cancel = () => {
    setOpen(false);
    setPassword('');
    remove.reset();
  };

  return (
    <section className="panel space-y-4" aria-labelledby="delete-title">
      <h2 id="delete-title" className="text-xl font-bold">
        {m.account.deleteTitle}
      </h2>
      <p className="text-ink-soft">{m.account.deleteIntro}</p>
      {!open ? (
        <button type="button" className="btn btn-danger" onClick={() => setOpen(true)}>
          {m.account.deleteStart}
        </button>
      ) : (
        <form className="space-y-4" onSubmit={onSubmit}>
          <div>
            <label className="label" htmlFor="delete-password">
              {m.account.deletePassword}
            </label>
            <input
              id="delete-password"
              className="field"
              type="password"
              autoComplete="current-password"
              required
              autoFocus
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>
          {remove.isError && <Notice tone="error">{errorMessage(m, remove.error)}</Notice>}
          <div className="flex flex-wrap gap-2">
            <button
              type="submit"
              className="btn btn-danger"
              disabled={remove.isPending || !password}
            >
              {m.account.deleteConfirm}
            </button>
            <button type="button" className="btn btn-ghost" onClick={cancel}>
              {m.account.deleteCancel}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

const TAB_IDS = ['account', 'learning', 'ai', 'accounts', 'system'] as const;
type TabId = (typeof TAB_IDS)[number];

export function SettingsPage() {
  const { m } = useI18n();
  const update = useUpdateProfile();
  const [params, setParams] = useSearchParams();
  // Null for one render right after signing out, before AuthGate takes over.
  const user = useMe().data;
  const settings = useSettings(Boolean(user));
  if (!user) return null;
  const admin = user.role === 'admin';
  const tabs: readonly TabId[] = admin ? TAB_IDS : ['account', 'learning'];
  const requested = params.get('tab');
  const tab = tabs.find((id) => id === requested) ?? 'account';
  const select = (id: TabId) => setParams(id === 'account' ? {} : { tab: id }, { replace: true });

  return (
    <>
      <PageHeader title={m.settings.title} />
      <div
        role="tablist"
        aria-label={m.settings.tabsLabel}
        className="mb-6 flex max-w-xl gap-1 overflow-x-auto rounded-full bg-surface-2 p-1"
      >
        {tabs.map((id) => (
          <button
            key={id}
            type="button"
            role="tab"
            id={`settings-tab-${id}`}
            aria-selected={tab === id}
            aria-controls="settings-panel"
            onClick={() => select(id)}
            className={[
              'min-h-9 flex-1 shrink-0 rounded-full px-4 text-sm font-semibold whitespace-nowrap transition-colors',
              tab === id ? 'bg-primary text-on-primary' : 'text-ink-soft hover:text-ink',
            ].join(' ')}
          >
            {m.settings.tabs[id]}
          </button>
        ))}
      </div>
      <div
        id="settings-panel"
        role="tabpanel"
        aria-labelledby={`settings-tab-${tab}`}
        className="grid max-w-xl grid-cols-[minmax(0,1fr)] gap-6"
      >
        {tab === 'account' && (
          <>
            <section className="panel">
              <span className="label">{m.settings.language}</span>
              <LanguageSwitch
                label={m.settings.language}
                onChange={(uiLanguage) => update.mutate({ uiLanguage })}
              />
            </section>
            <AccountSection user={user} />
            <PasswordSection />
            {!admin && <DeleteAccountSection />}
          </>
        )}
        {tab === 'learning' && settings.data && <LearningSection settings={settings.data} />}
        {tab === 'ai' && (
          <>
            <AiSettings />
            {settings.data && <AiTimeoutSection settings={settings.data} />}
          </>
        )}
        {tab === 'accounts' && <AccountsAdmin />}
        {tab === 'system' && (
          <>
            <BackupAdmin />
            <UpdateAdmin />
          </>
        )}
      </div>
    </>
  );
}
