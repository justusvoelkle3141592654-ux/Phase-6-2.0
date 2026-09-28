import { UI_LANGUAGES, type UiLanguage } from '@vokabeltrainer/shared';
import { useI18n } from '../i18n';

const LANGUAGE_NAMES = { de: 'Deutsch', en: 'English' } as const;

export function LanguageSwitch({
  label,
  onChange,
}: {
  label: string;
  onChange?: (lang: UiLanguage) => void;
}) {
  const { lang, setLang } = useI18n();
  return (
    <div role="radiogroup" aria-label={label} className="segmented">
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
        >
          {LANGUAGE_NAMES[code]}
        </button>
      ))}
    </div>
  );
}
