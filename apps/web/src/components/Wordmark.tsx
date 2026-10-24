import { useI18n } from '../i18n';

/** App logo: a "W" that draws itself in, followed by the app name. */
export function Wordmark() {
  const { m } = useI18n();
  return (
    <div className="wordmark flex items-center gap-2.5">
      <svg viewBox="0 0 64 64" className="size-7" aria-hidden="true">
        <rect width="64" height="64" rx="14" className="wordmark-tile fill-primary" />
        <path
          d="M18 22l7 20 7-14 7 14 7-20"
          pathLength={1}
          fill="none"
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="wordmark-w stroke-on-primary"
        />
      </svg>
      <span className="text-lg font-bold tracking-tight">{m.appName}</span>
    </div>
  );
}
