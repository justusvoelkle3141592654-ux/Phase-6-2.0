import { useState, type FormEvent } from 'react';
import { PASSWORD_MIN_LENGTH } from '@gero/shared';
import { LanguageSwitch } from '../components/LanguageSwitch';
import { Wordmark } from '../components/Wordmark';
import { useI18n } from '../i18n';
import { useLogin, useRegister } from '../lib/auth';
import { errorMessage } from '../lib/errors';

function browserTimezone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return undefined;
  }
}

/** Sign-in and registration on an index card. */
export function AuthPage() {
  const { m, lang } = useI18n();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const login = useLogin();
  const register = useRegister();
  const pending = login.isPending || register.isPending;
  const error = mode === 'login' ? login.error : register.error;

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (mode === 'login') {
      login.mutate({ email, password });
    } else {
      register.mutate({
        email,
        password,
        registrationCode: code,
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

  const title = mode === 'login' ? m.auth.loginTitle : m.auth.registerTitle;

  return (
    <div className="flex min-h-dvh flex-col items-center px-4 pt-[max(2rem,env(safe-area-inset-top))] pb-10">
      <div className="flex w-full max-w-md items-center justify-between">
        <Wordmark />
        <LanguageSwitch label={m.settings.language} />
      </div>

      <main className="index-card mt-8 w-full max-w-md">
        <div className="index-card-head">
          <h1 className="font-word text-3xl font-bold tracking-tight">{title}</h1>
        </div>
        <form className="index-card-body space-y-4" onSubmit={onSubmit} noValidate>
          <p className="text-ink-soft">
            {mode === 'login' ? m.auth.loginIntro : m.auth.registerIntro}
          </p>

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
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              minLength={mode === 'register' ? PASSWORD_MIN_LENGTH : undefined}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-describedby={mode === 'register' ? 'auth-password-hint' : undefined}
            />
            {mode === 'register' && (
              <p id="auth-password-hint" className="mt-1 text-sm text-ink-soft">
                {m.auth.passwordHint}
              </p>
            )}
          </div>

          {mode === 'register' && (
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

          {error && (
            <p
              role="alert"
              className="rounded-xl bg-falsch-soft px-3.5 py-2.5 text-sm font-semibold text-falsch"
            >
              {errorMessage(m, error)}
            </p>
          )}

          <button type="submit" className="btn btn-primary w-full" disabled={pending}>
            {mode === 'login' ? m.auth.login : m.auth.register}
          </button>
          <button type="button" className="btn btn-ghost w-full" onClick={switchMode}>
            {mode === 'login' ? m.auth.toRegister : m.auth.toLogin}
          </button>
        </form>
      </main>
    </div>
  );
}
