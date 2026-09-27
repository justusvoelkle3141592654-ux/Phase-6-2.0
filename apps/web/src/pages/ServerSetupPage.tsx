import { useState, type FormEvent } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { APP_NAME, type HealthResponse } from '@gero/shared';
import { CenteredLayout } from '../components/CenteredLayout';
import { Notice } from '../components/Notice';
import { useI18n } from '../i18n';
import { ME_KEY } from '../lib/auth';
import { getServerUrl, normalizeServerUrl, setServerUrl } from '../lib/platform';

/** Android app: asks for the server address (home network over HTTP or internet over HTTPS). */
export function ServerSetupPage({ onDone }: { onDone: () => void }) {
  const { m } = useI18n();
  const qc = useQueryClient();
  const [url, setUrl] = useState(getServerUrl() ?? '');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const base = normalizeServerUrl(url);
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${base}/api/health`, { signal: AbortSignal.timeout(8000) });
      const health = (await res.json()) as HealthResponse;
      if (!res.ok || health.name !== APP_NAME) throw new Error('not_gero');
      await setServerUrl(base);
      qc.setQueryData(ME_KEY, null);
      onDone();
    } catch (err) {
      setError((err as Error).message === 'not_gero' ? m.app.notGero : m.app.unreachable);
    } finally {
      setBusy(false);
    }
  };

  return (
    <CenteredLayout>
      <div className="panel sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight">{m.app.serverTitle}</h1>
        <p className="mt-1.5 text-ink-soft">{m.app.serverIntro}</p>
        <form className="mt-6 space-y-4" onSubmit={submit}>
          <div>
            <label className="label" htmlFor="server-url">
              {m.app.serverUrl}
            </label>
            <input
              id="server-url"
              className="field font-mono"
              inputMode="url"
              autoCapitalize="off"
              autoCorrect="off"
              spellCheck={false}
              placeholder="http://192.168.1.20:3000"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
            />
          </div>
          {error && <Notice tone="error">{error}</Notice>}
          <button type="submit" className="btn btn-primary w-full" disabled={busy || !url.trim()}>
            {busy ? m.app.connecting : m.app.connect}
          </button>
        </form>
      </div>
    </CenteredLayout>
  );
}
