import { useEffect, useState, type FormEvent } from 'react';
import { RefreshCw } from 'lucide-react';
import type { UpdateStatusDto } from '@wordflow/shared';
import { useI18n } from '../../i18n';
import { useSaveUpdateSource, useStartUpdate, useUpdateStatus } from '../../lib/admin';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';

const short = (sha: string | null) => (sha ? sha.slice(0, 7) : '–');

/** Repository, branch and GitHub token the update installs from. */
function UpdateSourceForm({ status, disabled }: { status: UpdateStatusDto; disabled: boolean }) {
  const { m } = useI18n();
  const save = useSaveUpdateSource();
  const [repo, setRepo] = useState(status.repo);
  const [branch, setBranch] = useState(status.branch);
  const [token, setToken] = useState('');

  // Follow the server after a save or a fresh check.
  useEffect(() => {
    setRepo(status.repo);
    setBranch(status.branch);
  }, [status.repo, status.branch]);

  const valid = repo.trim() !== '' && branch.trim() !== '';
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!valid) return;
    save.mutate(
      { repo, branch, ...(token.trim() ? { token } : {}) },
      { onSuccess: () => setToken('') },
    );
  };
  const removeToken = () => {
    if (window.confirm(m.admin.tokenRemoveConfirm))
      save.mutate({ repo: status.repo, branch: status.branch, token: '' });
  };

  return (
    <form className="space-y-3 rounded-2xl bg-surface-2 p-4" onSubmit={submit}>
      <h3 className="font-bold">{m.admin.sourceTitle}</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor="update-repo">
            {m.admin.repo}
          </label>
          <input
            id="update-repo"
            className="field bg-surface"
            autoComplete="off"
            spellCheck={false}
            value={repo}
            onChange={(e) => setRepo(e.target.value)}
            aria-describedby="update-repo-hint"
          />
          <p id="update-repo-hint" className="hint">
            {m.admin.repoHint}
          </p>
        </div>
        <div>
          <label className="label" htmlFor="update-branch">
            {m.admin.branch}
          </label>
          <input
            id="update-branch"
            className="field bg-surface"
            autoComplete="off"
            spellCheck={false}
            value={branch}
            onChange={(e) => setBranch(e.target.value)}
          />
        </div>
      </div>
      <div>
        <label className="label" htmlFor="update-token">
          {m.admin.token}
        </label>
        <input
          id="update-token"
          className="field bg-surface"
          type="password"
          autoComplete="off"
          placeholder={status.hasToken ? '••••••••' : 'github_pat_…'}
          value={token}
          onChange={(e) => setToken(e.target.value)}
          aria-describedby="update-token-hint"
        />
        <p id="update-token-hint" className="hint">
          {status.hasToken ? m.admin.tokenSet : m.admin.tokenMissing}
        </p>
      </div>
      {save.isError && <Notice tone="error">{errorMessage(m, save.error)}</Notice>}
      {save.isSuccess && !save.isPending && <Notice tone="ok">{m.admin.sourceSaved}</Notice>}
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          className="btn btn-primary"
          disabled={!valid || disabled || save.isPending}
        >
          {m.admin.sourceSave}
        </button>
        {status.hasToken && (
          <button
            type="button"
            className="btn btn-ghost"
            disabled={disabled || save.isPending}
            onClick={removeToken}
          >
            {m.admin.tokenRemove}
          </button>
        )}
      </div>
    </form>
  );
}

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
      {data && <UpdateSourceForm status={data} disabled={running} />}
      {data?.log && (
        <pre className="max-h-64 overflow-auto rounded-xl bg-surface-2 p-3 text-xs whitespace-pre-wrap">
          {data.log}
        </pre>
      )}
    </section>
  );
}
