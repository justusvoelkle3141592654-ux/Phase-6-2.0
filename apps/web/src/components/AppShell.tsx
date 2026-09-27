import { NavLink, Outlet } from 'react-router';
import { Camera, GraduationCap, House, Layers, Settings, type LucideIcon } from 'lucide-react';
import { useI18n } from '../i18n';
import type { Messages } from '../i18n/de';
import { Wordmark } from './Wordmark';

interface Tab {
  to: string;
  label: keyof Messages['nav'];
  icon: LucideIcon;
}

export const TABS: Tab[] = [
  { to: '/', label: 'home', icon: House },
  { to: '/upload', label: 'upload', icon: Camera },
  { to: '/packages', label: 'packages', icon: Layers },
  { to: '/learn', label: 'learn', icon: GraduationCap },
  { to: '/settings', label: 'settings', icon: Settings },
];

/** Highlighter stroke behind the active label. */
const marker =
  'bg-[linear-gradient(transparent_68%,var(--color-marker)_68%,var(--color-marker)_94%,transparent_94%)]';

export function AppShell() {
  const { m } = useI18n();

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="border-b-2 border-rule pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex w-full max-w-5xl items-end justify-between px-4 pt-4 md:pt-6">
          <div className="pb-3">
            <Wordmark />
          </div>
          {/* Desktop: index-card tabs sitting on the header rule. */}
          <nav className="-mb-[2px] hidden gap-1 md:flex" aria-label="Hauptnavigation">
            {TABS.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  [
                    'flex items-center gap-2 rounded-t-xl border-2 px-4 pt-2 pb-2.5 text-[0.95rem] font-semibold transition-colors',
                    isActive
                      ? 'border-rule border-b-paper bg-paper text-ink'
                      : 'border-transparent text-ink-soft hover:bg-surface-2 hover:text-ink',
                  ].join(' ')
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon
                      className={isActive ? 'size-4.5 text-tinte' : 'size-4.5'}
                      aria-hidden="true"
                    />
                    <span className={isActive ? marker : undefined}>{m.nav[label]}</span>
                  </>
                )}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pt-6 pb-28 md:pt-8 md:pb-12">
        <Outlet />
      </main>

      {/* Mobile: bottom tab bar. */}
      <nav
        className="fixed inset-x-0 bottom-0 z-20 border-t border-rule bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
        aria-label="Hauptnavigation"
      >
        <ul className="grid grid-cols-5">
          {TABS.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  [
                    'flex flex-col items-center gap-1 px-1 pt-2 pb-2 text-[0.7rem] font-semibold',
                    isActive ? 'text-ink' : 'text-ink-soft',
                  ].join(' ')
                }
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={[
                        'grid h-8 w-12 place-items-center rounded-full transition-colors',
                        isActive ? 'bg-marker text-ink' : '',
                      ].join(' ')}
                    >
                      <Icon className="size-5" aria-hidden="true" />
                    </span>
                    <span className="truncate">{m.nav[label]}</span>
                  </>
                )}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
