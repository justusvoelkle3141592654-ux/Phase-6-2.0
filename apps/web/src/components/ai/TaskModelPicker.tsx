import { useEffect, useId } from 'react';
import type { AiTask, ProviderDto, TaskModelDto } from '@vokabeltrainer/shared';
import { useI18n } from '../../i18n';
import { useModels } from '../../lib/ai';
import { providerErrorOf, ProviderErrorNotice } from './ProviderError';

export type TaskModelValue = Pick<TaskModelDto, 'providerId' | 'model' | 'reasoning'>;

/**
 * Provider + model for one task. The model list is loaded live from the
 * provider (image-capable only for "vision"); the name can also be typed.
 */
export function TaskModelPicker({
  task,
  providers,
  value,
  onChange,
  fixedProvider,
}: {
  task: AiTask;
  providers: ProviderDto[];
  value: TaskModelValue | null;
  onChange: (value: TaskModelValue) => void;
  /** Hide the provider select (the wizard chose it one step earlier). */
  fixedProvider?: boolean;
}) {
  const { m } = useI18n();
  const id = useId();
  const providerId = value?.providerId ?? providers[0]?.id ?? null;
  const provider = providers.find((p) => p.id === providerId);
  const models = useModels(providerId, task === 'vision');
  const list = models.data ?? [];
  const inList = list.some((mm) => mm.id === value?.model);
  const providerError = providerErrorOf(models.error);

  // Pick the first model when the provider changes and nothing fitting is chosen.
  useEffect(() => {
    if (providerId === null || !models.data || models.data.length === 0) return;
    if (value?.providerId === providerId && value.model) return;
    const first = models.data[0]!;
    onChange({ providerId, model: first.id, reasoning: first.reasoning });
  }, [providerId, models.data, value, onChange]);

  const setModel = (model: string) => {
    if (providerId === null) return;
    const info = list.find((mm) => mm.id === model);
    onChange({ providerId, model, reasoning: info?.reasoning ?? null });
  };

  return (
    <div className="space-y-3">
      {!fixedProvider && (
        <div>
          <label className="label" htmlFor={`${id}-provider`}>
            {m.ai.provider}
          </label>
          <select
            id={`${id}-provider`}
            className="field"
            value={providerId ?? ''}
            onChange={(e) =>
              onChange({ providerId: Number(e.target.value), model: '', reasoning: null })
            }
          >
            {providers.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
      )}
      <div>
        <label className="label" htmlFor={`${id}-model`}>
          {m.ai.model}
        </label>
        {list.length > 0 ? (
          <select
            id={`${id}-model`}
            className="field"
            value={inList ? value!.model : ''}
            onChange={(e) => setModel(e.target.value)}
          >
            {!inList && <option value="">{value?.model || '–'}</option>}
            {list.map((mm) => (
              <option key={mm.id} value={mm.id}>
                {mm.name && mm.name !== mm.id ? `${mm.id} (${mm.name})` : mm.id}
              </option>
            ))}
          </select>
        ) : (
          <input
            id={`${id}-model`}
            className="field font-mono"
            placeholder={m.ai.modelManual}
            value={value?.model ?? ''}
            onChange={(e) => setModel(e.target.value)}
          />
        )}
        <p className="hint">
          {models.isPending && providerId !== null
            ? m.ai.modelsLoading
            : models.isError
              ? m.ai.modelsFailed
              : list.length === 0
                ? m.ai.modelsEmpty
                : m.ai.taskHints[task]}
        </p>
        {task === 'vision' && list.length > 0 && list.every((mm) => mm.vision === null) && (
          <p className="hint">{m.ai.visionUnknown}</p>
        )}
      </div>
      {providerError && <ProviderErrorNotice error={providerError} provider={provider} />}
    </div>
  );
}
