import type { ReactNode } from 'react';
import { useI18n } from '../i18n';
import { LanguageSwitch } from './LanguageSwitch';
import { Wordmark } from './Wordmark';

/** Full-screen layout for sign-in and setup: brand and language on top, content centred. */
export function CenteredLayout({
  children,
  onLanguageChange,
  width = 'max-w-md',
  showLanguage = true,
}: {
  children: ReactNode;
  onLanguageChange?: Parameters<typeof LanguageSwitch>[0]['onChange'];
  width?: string;
  showLanguage?: boolean;
}) {
  const { m } = useI18n();
  return (
    <div className="flex min-h-dvh flex-col items-center px-4 pt-[max(1.25rem,env(safe-area-inset-top))] pb-10">
      <div className={`flex w-full items-center justify-between ${width}`}>
        <Wordmark />
        {showLanguage && <LanguageSwitch label={m.settings.language} onChange={onLanguageChange} />}
      </div>
      <main className={`mt-8 w-full sm:mt-14 ${width}`}>{children}</main>
    </div>
  );
}
