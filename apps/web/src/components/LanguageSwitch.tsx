import { UI_LANGUAGES, type UiLanguage } from '@gero/shared';
import { useI18n } from '../i18n';

const LANGUAGE_NAMES = { de: 'Deutsch', en: 'English' } as const;

export function LanguageSwitch({
  id,
  label,
  onChange,
}: {
  id?: string;
  label: string;
  onChange?: (lang: UiLanguage) => void;
}) {
  const { lang, setLang } = useI18n();
  return (
    <div
      id={id}
      role="radiogroup"
      aria-label={label}
      className="inline-flex rounded-xl bg-surface-2 p-1"
    >
      {UI_LANGUAGES.map((code) => (
        <button
          key={code}
          type="button"
          role="radio"
          aria-checked={lang === code}
          onClick={() => {
            setLang(code);
            onChange?.(code);
          }}
          className={[
            'rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors',
            lang === code ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink',
          ].join(' ')}
        >
          {LANGUAGE_NAMES[code]}
        </button>
      ))}
    </div>
  );
}
