import { useEffect, type ReactNode } from 'react';
import { useI18n } from '../i18n';
import { useMe } from '../lib/auth';
import { errorMessage } from '../lib/errors';
import { AuthPage } from '../pages/AuthPage';

/** Shows the sign-in page until a user is signed in. */
export function AuthGate({ children }: { children: ReactNode }) {
  const { m, setLang } = useI18n();
  const me = useMe();
  const uiLanguage = me.data?.uiLanguage;

  // The account's language wins over the one stored in this browser.
  useEffect(() => {
    if (uiLanguage) setLang(uiLanguage);
  }, [uiLanguage, setLang]);

  if (me.isPending) {
    return (
      <div className="grid min-h-dvh place-items-center text-ink-soft" role="status">
        {m.common.loading}
      </div>
    );
  }
  if (me.isError) {
    return (
      <div className="grid min-h-dvh place-items-center px-4 text-center">
        <div>
          <p role="alert" className="text-ink-soft">
            {errorMessage(m, me.error)}
          </p>
          <button
            type="button"
            className="btn btn-secondary mt-4"
            onClick={() => void me.refetch()}
          >
            {m.common.retry}
          </button>
        </div>
      </div>
    );
  }
  if (!me.data) return <AuthPage />;
  return <>{children}</>;
}
