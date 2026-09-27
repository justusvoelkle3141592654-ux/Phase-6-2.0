import { useI18n } from '../i18n';

/** App name next to a small stack of index cards. */
export function Wordmark() {
  const { m } = useI18n();
  return (
    <div className="flex items-center gap-2.5">
      <svg viewBox="0 0 40 32" className="h-7 w-9" aria-hidden="true">
        <rect
          x="6"
          y="2"
          width="30"
          height="22"
          rx="4"
          className="fill-stufe-2"
          transform="rotate(-7 21 13)"
        />
        <rect x="3" y="7" width="32" height="22" rx="4" className="fill-tinte" />
        <rect x="3" y="7" width="32" height="6" rx="3" className="fill-tinte-strong" />
        <path
          d="M9 19h20M9 24h13"
          className="stroke-white"
          strokeWidth="2.5"
          strokeLinecap="round"
        />
      </svg>
      <div className="leading-none">
        <span className="font-word text-2xl font-bold tracking-tight">{m.appName}</span>
        <span className="ml-2 hidden text-sm text-ink-soft sm:inline">{m.tagline}</span>
      </div>
    </div>
  );
}
