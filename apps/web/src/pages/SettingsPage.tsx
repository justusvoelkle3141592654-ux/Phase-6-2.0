import { UI_LANGUAGES } from '@gero/shared';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';

const LANGUAGE_NAMES = { de: 'Deutsch', en: 'English' } as const;

export function SettingsPage() {
  const { m, lang, setLang } = useI18n();
  return (
    <>
      <PageHeader title={m.settings.title} />
      <section className="panel max-w-xl">
        <label className="label" htmlFor="ui-lang">
          {m.settings.language}
        </label>
        <div id="ui-lang" role="radiogroup" className="inline-flex rounded-xl bg-surface-2 p-1">
          {UI_LANGUAGES.map((code) => (
            <button
              key={code}
              type="button"
              role="radio"
              aria-checked={lang === code}
              onClick={() => setLang(code)}
              className={[
                'rounded-lg px-4 py-1.5 text-sm font-semibold transition-colors',
                lang === code ? 'bg-surface text-ink shadow-sm' : 'text-ink-soft hover:text-ink',
              ].join(' ')}
            >
              {LANGUAGE_NAMES[code]}
            </button>
          ))}
        </div>
      </section>
    </>
  );
}
