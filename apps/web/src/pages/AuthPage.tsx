import { useEffect, useRef, useState, type FormEvent } from 'react';
import { PASSWORD_MIN_LENGTH } from '@wordflow/shared';
import { CenteredLayout } from '../components/CenteredLayout';
import { Notice } from '../components/Notice';
import { useI18n } from '../i18n';
import { api } from '../lib/api';
import { useLogin, useRegister } from '../lib/auth';
import { getServerUrl } from '../lib/platform';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';

function browserTimezone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return undefined;
  }
}

type OfflineAccount = { email: string; name: string };
type ModeResponse = {
  offlineMode: boolean;
  email?: string;
  password?: string;
  accounts?: OfflineAccount[];
};
type OfflineInfo = { email: string; password: string; accounts: OfflineAccount[] };

/** Sign-in and registration. */
export function AuthPage({ onChangeServer }: { onChangeServer?: () => void } = {}) {
  const { m, lang } = useI18n();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [offline, setOffline] = useState<OfflineInfo | null>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const login = useLogin();
  const register = useRegister();
  const pending = login.isPending || register.isPending;
  const error = mode === 'login' ? login.error : register.error;

  useEffect(() => {
    void api<ModeResponse>('/auth/mode')
      .then((data) => {
        if (!data.offlineMode) return;
        setOffline({
          email: data.email ?? 'offline@local.test',
          password: data.password ?? 'offline',
          accounts: data.accounts ?? [],
        });
      })
      .catch(() => undefined);
  }, []);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (mode === 'login') {
      login.mutate({ email, password });
    } else {
      register.mutate({
        email,
        password,
        registrationCode: offline ? undefined : code,
        uiLanguage: lang,
        timezone: browserTimezone(),
      });
    }
  };

  const switchMode = () => {
    login.reset();
    register.reset();
    setMode(mode === 'login' ? 'register' : 'login');
  };

  // The default account has its own "continue offline" button.
  const others = offline?.accounts.filter((a) => a.email !== offline.email) ?? [];
  const title = mode === 'login' ? m.auth.loginTitle : m.auth.registerTitle;

  return (
    <CenteredLayout>
      <div className="panel sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        <p className="mt-1.5 text-ink-soft">
          {mode === 'login'
            ? m.auth.loginIntro
            : offline
              ? m.auth.offlineRegisterIntro
              : m.auth.registerIntro}
        </p>
        <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
          <div>
            <label className="label" htmlFor="auth-email">
              {m.auth.email}
            </label>
            <input
              id="auth-email"
              className="field"
              type="email"
              autoComplete="email"
              inputMode="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div>
            <label className="label" htmlFor="auth-password">
              {m.auth.password}
            </label>
            <input
              id="auth-password"
              className="field"
              type="password"
              ref={passwordRef}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={mode === 'register' ? PASSWORD_MIN_LENGTH : undefined}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-describedby={mode === 'register' ? 'auth-password-hint' : undefined}
            />
            {mode === 'register' && (
              <p id="auth-password-hint" className="hint">
                {m.auth.passwordHint}
              </p>
            )}
          </div>

          {mode === 'register' && !offline && (
            <div>
              <label className="label" htmlFor="auth-code">
                {m.auth.registrationCode}
              </label>
              <input
                id="auth-code"
                className="field font-mono uppercase"
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                required
                value={code}
                onChange={(e) => setCode(e.target.value)}
              />
            </div>
          )}

          {error && <Notice tone="error">{errorMessage(m, error)}</Notice>}

          {offline && mode === 'login' && (
            <button
              type="button"
              className="btn btn-secondary w-full"
              onClick={() => {
                setEmail(offline.email);
                setPassword(offline.password);
                login.mutate({ email: offline.email, password: offline.password });
              }}
              disabled={pending}
            >
              {m.auth.continueOffline}
            </button>
          )}

          <div className="space-y-2 pt-2">
            <button type="submit" className="btn btn-primary w-full" disabled={pending}>
              {mode === 'login' ? m.auth.login : m.auth.register}
            </button>
            <button type="button" className="btn btn-ghost w-full" onClick={switchMode}>
              {mode === 'login' ? m.auth.toRegister : m.auth.toLogin}
            </button>
          </div>
        </form>
        {offline && mode === 'login' && others.length > 0 && (
          <section className="mt-6" aria-labelledby="offline-accounts">
            <h2 id="offline-accounts" className="text-sm font-semibold">
              {m.auth.offlineAccounts}
            </h2>
            <p className="hint">{m.auth.offlineAccountsHint}</p>
            <ul className="mt-2 space-y-2">
              {others.map((account) => (
                <li key={account.email}>
                  <button
                    type="button"
                    className="btn btn-ghost w-full justify-start text-left"
                    aria-pressed={email === account.email}
                    onClick={() => {
                      login.reset();
                      setEmail(account.email);
                      setPassword('');
                      passwordRef.current?.focus();
                    }}
                  >
                    <span className="truncate">
                      {account.name ? `${account.name} · ${account.email}` : account.email}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}
        {onChangeServer && (
          <p className="mt-6 flex flex-wrap items-center justify-center gap-2 text-sm text-ink-soft">
            <span className="break-all">{fill(m.app.server, { url: getServerUrl() ?? '' })}</span>
            <button
              type="button"
              className="font-semibold text-ink underline"
              onClick={onChangeServer}
            >
              {m.app.changeServer}
            </button>
          </p>
        )}
      </div>
    </CenteredLayout>
  );
}
