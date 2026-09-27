import type { ProviderDto, ProviderErrorDto } from '@gero/shared';
import { useI18n } from '../../i18n';
import { ApiError } from '../../lib/api';
import { Notice } from '../Notice';

export function providerErrorOf(error: unknown): ProviderErrorDto | null {
  if (error instanceof ApiError && error.code === 'provider_error') {
    return (error.body?.provider as ProviderErrorDto) ?? null;
  }
  return null;
}

/** Explains a provider error; for Pollinations 401/402 also where to get a key. */
export function ProviderErrorNotice({
  error,
  provider,
}: {
  error: ProviderErrorDto;
  provider?: ProviderDto;
}) {
  const { m } = useI18n();
  const base = m.ai.errors[error.code];
  const detail =
    error.message && error.code === 'http' ? ` (${error.status ?? ''} ${error.message})` : '';
  const pollinations =
    provider?.kind === 'pollinations' && (error.code === 'auth' || error.code === 'payment')
      ? ` ${m.ai.pollinationsKey}`
      : '';
  return <Notice tone="error">{`${base}${detail}${pollinations}`}</Notice>;
}
