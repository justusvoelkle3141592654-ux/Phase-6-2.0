import { useCallback, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import {
  AI_TASKS,
  PROVIDER_PRESETS,
  type AiConfigDto,
  type AiTask,
  type ProviderDto,
} from '@wordflow/shared';
import { useI18n } from '../../i18n';
import {
  useAiConfig,
  useDeleteProvider,
  useLatency,
  useSetTaskModel,
  useUpdateProvider,
} from '../../lib/ai';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';
import { ProviderForm } from './ProviderForm';
import { TaskModelPicker, type TaskModelValue } from './TaskModelPicker';
import { TestButton } from './TestButton';

/** Replace or remove the API key of one provider. */
function ProviderKeyForm({ provider, label }: { provider: ProviderDto; label?: string }) {
  const { m } = useI18n();
  const [key, setKey] = useState('');
  const update = useUpdateProvider();
  if (PROVIDER_PRESETS[provider.kind].key === 'none') return null;
  const inputId = `key-${label ? 'task-' : ''}${provider.id}`;
  return (
    <div className="space-y-1.5">
      {label && (
        <p className="text-sm text-ink-soft">
          <label htmlFor={inputId} className="font-semibold text-ink">
            {label}
          </label>{' '}
          ·{' '}
          {provider.hasKey
            ? fill(m.ai.apiKeySet, { hint: provider.keyHint ?? '' })
            : m.ai.apiKeyNone}
        </p>
      )}
      <form
        className="flex flex-wrap gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (key.trim())
            update.mutate({ id: provider.id, apiKey: key.trim() }, { onSuccess: () => setKey('') });
        }}
      >
        {!label && (
          <label className="sr-only" htmlFor={inputId}>
            {m.ai.apiKey}
          </label>
        )}
        <input
          id={inputId}
          className="field min-w-0 flex-1 font-mono"
          type="password"
          autoComplete="off"
          placeholder={m.ai.apiKeyReplace}
          value={key}
          onChange={(e) => setKey(e.target.value)}
        />
        <button
          type="submit"
          className="btn btn-secondary"
          disabled={!key.trim() || update.isPending}
        >
          {m.ai.save}
        </button>
        {provider.hasKey && (
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => update.mutate({ id: provider.id, apiKey: null })}
            disabled={update.isPending}
          >
            {m.ai.removeKey}
          </button>
        )}
      </form>
      {update.isError && <Notice tone="error">{errorMessage(m, update.error)}</Notice>}
    </div>
  );
}

function ProviderRow({ provider }: { provider: ProviderDto }) {
  const { m } = useI18n();
  const remove = useDeleteProvider();

  return (
    <li className="space-y-3 px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="font-semibold">{provider.name}</p>
          <p className="text-sm break-all text-ink-soft">
            {provider.baseUrl} · {m.ai.protocols[provider.protocol]}
          </p>
          <p className="text-sm text-ink-soft">
            {provider.hasKey
              ? fill(m.ai.apiKeySet, { hint: provider.keyHint ?? '' })
              : m.ai.apiKeyNone}
          </p>
        </div>
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-3 text-sm"
          disabled={remove.isPending}
          onClick={() => {
            if (window.confirm(fill(m.ai.deleteConfirm, { name: provider.name })))
              remove.mutate(provider.id);
          }}
        >
          <Trash2 className="size-4" aria-hidden="true" />
          {m.ai.delete}
        </button>
      </div>
      <ProviderKeyForm provider={provider} />
      <TestButton provider={provider} />
    </li>
  );
}

function TaskRow({ task, config }: { task: AiTask; config: AiConfigDto }) {
  const { m } = useI18n();
  const current = config.tasks[task];
  const [value, setValue] = useState<TaskModelValue | null>(current);
  const [adding, setAdding] = useState(false);
  const save = useSetTaskModel();
  const provider = config.providers.find((p) => p.id === value?.providerId);
  const onChange = useCallback(
    (v: TaskModelValue) => {
      save.reset();
      setValue(v);
    },
    [save],
  );
  const changed =
    value && (value.providerId !== current?.providerId || value.model !== current?.model);

  return (
    <li className="space-y-3 px-5 py-4">
      <div>
        <p className="font-semibold">{m.ai.taskNames[task]}</p>
        <p className="text-sm text-ink-soft">
          {current
            ? `${config.providers.find((p) => p.id === current.providerId)?.name ?? ''} · ${current.model}`
            : m.ai.notSet}
        </p>
      </div>
      {adding ? (
        <div className="rounded-2xl border border-rule p-4">
          <ProviderForm
            onCancel={() => setAdding(false)}
            onDone={(p) => {
              setAdding(false);
              onChange({ providerId: p.id, model: '', reasoning: null });
            }}
          />
        </div>
      ) : (
        <TaskModelPicker
          task={task}
          providers={config.providers}
          value={value}
          onChange={onChange}
          onAddProvider={() => setAdding(true)}
        />
      )}
      {provider && !adding && (
        <ProviderKeyForm
          key={provider.id}
          provider={provider}
          label={fill(m.ai.keyOf, { name: provider.name })}
        />
      )}
      <div className="flex flex-wrap items-start gap-2">
        <button
          type="button"
          className="btn btn-primary"
          disabled={!changed || !value?.model || save.isPending}
          onClick={() => value && save.mutate({ task, ...value })}
        >
          {m.ai.save}
        </button>
        {provider && value?.model && <TestButton provider={provider} model={value.model} />}
      </div>
      {save.isSuccess && <Notice tone="ok">{m.account.saved}</Notice>}
      {save.isError && <Notice tone="error">{errorMessage(m, save.error)}</Notice>}
    </li>
  );
}

function LatencyTable() {
  const { m } = useI18n();
  const stats = useLatency();
  const rows = (stats.data ?? []).filter((s) => s.count > 0);
  const ms = (v: number | null) => (v === null ? '–' : `${v} ms`);
  return (
    <section className="panel" aria-labelledby="latency-title">
      <h3 id="latency-title" className="text-lg font-bold">
        {m.ai.latency}
      </h3>
      <p className="mb-3 text-sm text-ink-soft">{m.ai.latencyHint}</p>
      {rows.length === 0 ? (
        <p className="text-ink-soft">{m.ai.noData}</p>
      ) : (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full min-w-[34rem] text-left text-sm tabular-nums">
            <thead className="text-ink-soft">
              <tr>
                {Object.values(m.ai.latencyCols).map((c) => (
                  <th key={c} className="px-2 py-1.5 font-semibold">
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-rule">
              {rows.map((s) => (
                <tr key={s.task}>
                  <td className="px-2 py-2 font-semibold">
                    {s.task === 'test' ? m.ai.latencyTest : m.ai.taskNames[s.task]}
                  </td>
                  <td className="px-2 py-2">
                    {s.count}
                    {s.failures > 0 && <span className="text-falsch"> ({s.failures} ✕)</span>}
                  </td>
                  <td className="px-2 py-2">{ms(s.medianTtftMs)}</td>
                  <td className="px-2 py-2">{ms(s.medianTotalMs)}</td>
                  <td className="px-2 py-2">
                    {ms(s.p90TotalMs)} / {ms(s.maxTotalMs)}
                  </td>
                  <td className="px-2 py-2">{s.medianTokensPerSec ?? '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/** AI section of the settings page. */
export function AiSettings() {
  const { m } = useI18n();
  const config = useAiConfig();
  const [adding, setAdding] = useState(false);

  if (config.isPending) return <p className="text-ink-soft">{m.common.loading}</p>;
  if (config.isError) return <Notice tone="error">{errorMessage(m, config.error)}</Notice>;

  return (
    <>
      <section className="panel !p-0" aria-labelledby="tasks-title">
        <div className="px-5 pt-5 sm:px-6 sm:pt-6">
          <h2 id="tasks-title" className="text-xl font-bold">
            {m.ai.title}: {m.ai.tasks}
          </h2>
          <p className="mt-1 text-sm text-ink-soft">{m.ai.tasksHint}</p>
        </div>
        <ul className="mt-2 divide-y divide-rule">
          {AI_TASKS.map((task) => (
            <TaskRow
              key={`${task}-${config.data.tasks[task]?.providerId}-${config.data.tasks[task]?.model}`}
              task={task}
              config={config.data}
            />
          ))}
        </ul>
      </section>

      <section className="panel !p-0" aria-labelledby="providers-title">
        <div className="flex flex-wrap items-center justify-between gap-2 px-5 pt-5 sm:px-6 sm:pt-6">
          <h2 id="providers-title" className="text-xl font-bold">
            {m.ai.providers}
          </h2>
          {!adding && (
            <button
              type="button"
              className="btn btn-secondary min-h-9"
              onClick={() => setAdding(true)}
            >
              <Plus className="size-4" aria-hidden="true" />
              {m.ai.addProvider}
            </button>
          )}
        </div>
        <p className="px-5 pt-1 text-sm text-ink-soft sm:px-6">{m.ai.providersHint}</p>
        {adding && (
          <div className="px-5 pt-4 sm:px-6">
            <ProviderForm onDone={() => setAdding(false)} onCancel={() => setAdding(false)} />
          </div>
        )}
        <ul className="mt-2 divide-y divide-rule">
          {config.data.providers.map((p) => (
            <ProviderRow key={p.id} provider={p} />
          ))}
        </ul>
      </section>

      <LatencyTable />
    </>
  );
}
