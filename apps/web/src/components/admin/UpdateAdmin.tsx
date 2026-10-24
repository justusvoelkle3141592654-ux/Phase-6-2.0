import { RefreshCw } from 'lucide-react';
import { useI18n } from '../../i18n';
import { useStartUpdate, useUpdateStatus } from '../../lib/admin';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';

const short = (sha: string | null) => (sha ? sha.slice(0, 7) : '–');

/** Superuser: install the newest version from GitHub. */
export function UpdateAdmin() {
  const { m } = useI18n();
  const status = useUpdateStatus();
  const start = useStartUpdate();
  const data = status.data;
  const running = data?.running || start.isPending;

  return (
    <section className="panel space-y-4" aria-labelledby="update-title">
      <h2 id="update-title" className="text-xl font-bold">
        {m.admin.updateTitle}
      </h2>
      <p className="text-ink-soft">{m.admin.updateIntro}</p>
      {status.isPending ? (
        <p className="text-ink-soft">{m.common.loading}</p>
      ) : status.isError ? (
        <Notice tone="error">{errorMessage(m, status.error)}</Notice>
      ) : (
        <>
          <p className="text-sm">
            {fill(m.admin.versions, { current: short(data!.current), latest: short(data!.latest) })}
          </p>
          {data!.error && <Notice tone="error">{`${m.admin.checkFailed}: ${data!.error}`}</Notice>}
          {!data!.error && !running && (
            <Notice tone="ok">
              {data!.available ? m.admin.updateAvailable : m.admin.upToDate}
            </Notice>
          )}
        </>
      )}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          className="btn btn-primary"
          disabled={running || status.isPending}
          onClick={() => {
            if (window.confirm(m.admin.updateConfirm)) start.mutate();
          }}
        >
          <RefreshCw className={`size-4.5 ${running ? 'animate-spin' : ''}`} aria-hidden="true" />
          {running ? m.admin.updating : m.admin.update}
        </button>
        <button
          type="button"
          className="btn btn-ghost"
          disabled={status.isFetching}
          onClick={() => void status.refetch()}
        >
          {m.admin.check}
        </button>
      </div>
      {start.isError && <Notice tone="error">{errorMessage(m, start.error)}</Notice>}
      {data?.log && (
        <pre className="max-h-64 overflow-auto rounded-xl bg-surface-2 p-3 text-xs whitespace-pre-wrap">
          {data.log}
        </pre>
      )}
    </section>
  );
}
