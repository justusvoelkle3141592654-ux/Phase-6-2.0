import { useState, type FormEvent } from 'react';
import { LogOut } from 'lucide-react';
import { PASSWORD_MIN_LENGTH, type UserDto } from '@gero/shared';
import { LanguageSwitch } from '../components/LanguageSwitch';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { useChangePassword, useLogout, useMe, useUpdateProfile } from '../lib/auth';
import { errorMessage } from '../lib/errors';

function Notice({ tone, children }: { tone: 'ok' | 'error'; children: string }) {
  return (
    <p
      role={tone === 'error' ? 'alert' : 'status'}
      className={[
        'rounded-xl px-3.5 py-2.5 text-sm font-semibold',
        tone === 'ok' ? 'bg-richtig-soft text-richtig' : 'bg-falsch-soft text-falsch',
      ].join(' ')}
    >
      {children}
    </p>
  );
}

function AccountSection({ user }: { user: UserDto }) {
  const { m } = useI18n();
  const [name, setName] = useState(user.name);
  const update = useUpdateProfile();
  const logout = useLogout();

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    update.mutate({ name });
  };

  return (
    <section className="panel space-y-4" aria-labelledby="account-title">
      <h2 id="account-title" className="text-xl font-bold">
        {m.account.title}
      </h2>
      <p className="text-ink-soft">
        {m.account.signedInAs} <strong className="text-ink">{user.email}</strong>
      </p>
      <form className="flex flex-wrap items-end gap-3" onSubmit={onSubmit}>
        <div className="min-w-0 flex-1">
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
          <p id="pw-new-hint" className="mt-1 text-sm text-ink-soft">
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
  if (!user) return null;
  return (
    <>
      <PageHeader title={m.settings.title} />
      <div className="grid max-w-xl gap-6">
        <section className="panel">
          <span className="label">{m.settings.language}</span>
          <LanguageSwitch
            label={m.settings.language}
            onChange={(uiLanguage) => update.mutate({ uiLanguage })}
          />
        </section>
        <AccountSection user={user} />
        <PasswordSection />
      </div>
    </>
  );
}
