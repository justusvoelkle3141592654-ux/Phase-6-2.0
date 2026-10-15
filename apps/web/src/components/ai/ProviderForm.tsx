import { useState, type FormEvent, type KeyboardEvent } from 'react';
import {
  PROVIDER_KINDS,
  PROVIDER_PRESETS,
  type ProviderDto,
  type ProviderKind,
} from '@wordflow/shared';
import { useI18n } from '../../i18n';
import { useCreateProvider } from '../../lib/ai';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';

/** Adds a provider: preset or own server (URL + format), optional key. */
export function ProviderForm({
  onDone,
  onCancel,
  exclude = [],
  embedded = false,
}: {
  onDone: (provider: ProviderDto) => void;
  onCancel?: () => void;
  exclude?: ProviderKind[];
  /** Inside another form (the setup wizard): no nested <form>, Enter handled here. */
  embedded?: boolean;
}) {
  const { m } = useI18n();
  const kinds = PROVIDER_KINDS.filter((k) => k === 'custom' || !exclude.includes(k));
  const [kind, setKind] = useState<ProviderKind>(kinds[0]!);
  const [baseUrl, setBaseUrl] = useState('');
  const [protocol, setProtocol] = useState<'openai' | 'ollama'>('openai');
  const [apiKey, setApiKey] = useState('');
  const create = useCreateProvider();
  const preset = PROVIDER_PRESETS[kind];
  const needsUrl = kind === 'custom';
  const valid =
    (!needsUrl || /^https?:\/\/\S+$/i.test(baseUrl.trim())) &&
    (preset.key !== 'required' || apiKey.trim());

  const submit = (event: FormEvent | KeyboardEvent) => {
    event.preventDefault();
    if (!valid) return;
    create.mutate(
      {
        kind,
        baseUrl: needsUrl ? baseUrl.trim() : undefined,
        protocol: needsUrl ? protocol : undefined,
        apiKey: apiKey.trim() || undefined,
      },
      { onSuccess: onDone },
    );
  };

  const Wrapper = embedded ? 'div' : 'form';
  const onKeyDown = embedded
    ? (event: KeyboardEvent) => {
        if (event.key === 'Enter' && (event.target as HTMLElement).tagName === 'INPUT')
          submit(event);
      }
    : undefined;

  return (
    <Wrapper className="space-y-4" onSubmit={embedded ? undefined : submit} onKeyDown={onKeyDown}>
      <div>
        <label className="label" htmlFor="provider-kind">
          {m.ai.provider}
        </label>
        <select
          id="provider-kind"
          className="field"
          value={kind}
          onChange={(e) => setKind(e.target.value as ProviderKind)}
        >
          {kinds.map((k) => (
            <option key={k} value={k}>
              {m.ai.kinds[k]}
            </option>
          ))}
        </select>
      </div>
      {needsUrl && (
        <div className="grid gap-4 sm:grid-cols-[1fr_auto]">
          <div>
            <label className="label" htmlFor="provider-url">
              {m.ai.baseUrl}
            </label>
            <input
              id="provider-url"
              className="field"
              inputMode="url"
              placeholder="http://192.168.1.50:11434"
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              aria-describedby="provider-url-hint"
            />
          </div>
          <div>
            <label className="label" htmlFor="provider-protocol">
              {m.ai.protocol}
            </label>
            <select
              id="provider-protocol"
              className="field"
              value={protocol}
              onChange={(e) => setProtocol(e.target.value as 'openai' | 'ollama')}
            >
              <option value="openai">{m.ai.protocols.openai}</option>
              <option value="ollama">{m.ai.protocols.ollama}</option>
            </select>
          </div>
          <p id="provider-url-hint" className="hint sm:col-span-2">
            {m.ai.baseUrlHint}
          </p>
        </div>
      )}
      {preset.key !== 'none' && (
        <div>
          <label className="label" htmlFor="provider-key">
            {preset.key === 'required' ? m.ai.apiKey : m.ai.apiKeyOptional}
          </label>
          <input
            id="provider-key"
            className="field font-mono"
            type="password"
            autoComplete="off"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
          />
          <p className="hint">
            {m.ai.keyStored}
            {preset.keyUrl &&
              ` ${fill(m.ai.getKey, { url: preset.keyUrl.replace('https://', '') })}`}
          </p>
        </div>
      )}
      {create.isError && <Notice tone="error">{errorMessage(m, create.error)}</Notice>}
      <div className="flex flex-wrap gap-2">
        <button
          type={embedded ? 'button' : 'submit'}
          className="btn btn-primary"
          disabled={!valid || create.isPending}
          onClick={embedded ? submit : undefined}
        >
          {m.ai.add}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-ghost" onClick={onCancel}>
            {m.ai.cancel}
          </button>
        )}
      </div>
    </Wrapper>
  );
}
