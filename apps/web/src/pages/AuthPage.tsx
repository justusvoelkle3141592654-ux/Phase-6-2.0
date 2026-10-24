import { useEffect, useRef, useState, type FormEvent } from 'react';
import { CenteredLayout } from '../components/CenteredLayout';
import { Notice } from '../components/Notice';
import { useI18n } from '../i18n';
import { api } from '../lib/api';
import { useLogin } from '../lib/auth';
import { getServerUrl } from '../lib/platform';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';

type OfflineAccount = { email: string; name: string };
type ModeResponse = { offlineMode: boolean; accounts?: OfflineAccount[] };
type OfflineInfo = { accounts: OfflineAccount[] };

/** Sign-in. Accounts are created by the superuser. */
export function AuthPage({ onChangeServer }: { onChangeServer?: () => void } = {}) {
  const { m } = useI18n();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [offline, setOffline] = useState<OfflineInfo | null>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const login = useLogin();

  useEffect(() => {
    void api<ModeResponse>('/auth/mode')
      .then((data) => {
        if (!data.offlineMode) return;
        setOffline({ accounts: data.accounts ?? [] });
      })
      .catch(() => undefined);
  }, []);

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    login.mutate({ email, password });
  };

  const accounts = offline?.accounts ?? [];

  return (
    <CenteredLayout>
      <div className="panel sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight">{m.auth.loginTitle}</h1>
        <p className="mt-1.5 text-ink-soft">{m.auth.loginIntro}</p>
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
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {login.error && <Notice tone="error">{errorMessage(m, login.error)}</Notice>}

          <div className="space-y-2 pt-2">
            <button type="submit" className="btn btn-primary w-full" disabled={login.isPending}>
              {m.auth.login}
            </button>
            <p className="hint text-center">{m.auth.noAccount}</p>
          </div>
        </form>
        {offline && accounts.length > 0 && (
          <section className="mt-6" aria-labelledby="offline-accounts">
            <h2 id="offline-accounts" className="text-sm font-semibold">
              {m.auth.offlineAccounts}
            </h2>
            <p className="hint">{m.auth.offlineAccountsHint}</p>
            <ul className="mt-2 space-y-2">
              {accounts.map((account) => (
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
