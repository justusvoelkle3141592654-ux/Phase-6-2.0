import { useI18n } from '../i18n';

export function Wordmark() {
  const { m } = useI18n();
  return (
    <div className="flex items-center gap-2.5">
      <svg viewBox="0 0 64 64" className="size-7" aria-hidden="true">
        <rect width="64" height="64" rx="14" className="fill-primary" />
        <path
          d="M41 24.5A12 12 0 1 0 44 32h-11"
          fill="none"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="stroke-on-primary"
        />
      </svg>
      <span className="text-lg font-bold tracking-tight">{m.appName}</span>
    </div>
  );
}
