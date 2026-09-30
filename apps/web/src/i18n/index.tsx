import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { UI_LANGUAGES, type UiLanguage } from '@wordflow/shared';
import { de, type Messages } from './de';
import { en } from './en';

const catalogs: Record<UiLanguage, Messages> = { de, en };
const STORAGE_KEY = 'wordflow.lang';

interface I18nValue {
  lang: UiLanguage;
  m: Messages;
  setLang: (lang: UiLanguage) => void;
}

const I18nContext = createContext<I18nValue | null>(null);

function readStoredLang(): UiLanguage {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && (UI_LANGUAGES as readonly string[]).includes(stored)) return stored as UiLanguage;
  } catch {
    // Storage can be unavailable (private mode); fall back to German.
  }
  return 'de';
}

export function I18nProvider({ children, initial }: { children: ReactNode; initial?: UiLanguage }) {
  const [lang, setLangState] = useState<UiLanguage>(() => initial ?? readStoredLang());

  const setLang = useCallback((next: UiLanguage) => {
    setLangState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    document.documentElement.lang = lang;
  }, [lang]);

  const value = useMemo(() => ({ lang, m: catalogs[lang], setLang }), [lang, setLang]);
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n muss innerhalb von I18nProvider verwendet werden');
  return ctx;
}
