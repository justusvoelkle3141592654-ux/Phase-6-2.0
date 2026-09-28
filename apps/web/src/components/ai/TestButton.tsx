import type { ProviderDto } from '@vokabeltrainer/shared';
import { useI18n } from '../../i18n';
import { useTestConnection } from '../../lib/ai';
import { errorMessage } from '../../lib/errors';
import { fill } from '../../lib/format';
import { Notice } from '../Notice';
import { ProviderErrorNotice } from './ProviderError';

/** "Test connection": lists models and, with a model, measures a short reply. */
export function TestButton({ provider, model }: { provider: ProviderDto; model?: string }) {
  const { m } = useI18n();
  const test = useTestConnection();
  const result = test.data;

  return (
    <div className="space-y-2">
      <button
        type="button"
        className="btn btn-secondary"
        disabled={test.isPending}
        onClick={() => test.mutate({ providerId: provider.id, model: model || undefined })}
      >
        {test.isPending ? m.ai.testing : m.ai.test}
      </button>
      {test.isError && <Notice tone="error">{errorMessage(m, test.error)}</Notice>}
      {result?.ok && (
        <div
          role="status"
          className="rounded-xl bg-richtig-soft px-3.5 py-2.5 text-sm text-richtig"
        >
          <p className="font-semibold">{m.ai.testOk}</p>
          {result.models && (
            <p>{fill(m.ai.testModels, { n: result.models.count, ms: result.models.ms })}</p>
          )}
          {result.chat && (
            <p>
              {fill(m.ai.testChat, {
                ttft: result.chat.ttftMs ?? '–',
                total: result.chat.totalMs,
                tps: result.chat.tokensPerSec
                  ? fill(m.ai.tps, { n: result.chat.tokensPerSec })
                  : '',
              })}
            </p>
          )}
        </div>
      )}
      {result && !result.ok && result.error && (
        <ProviderErrorNotice error={result.error} provider={provider} />
      )}
    </div>
  );
}
