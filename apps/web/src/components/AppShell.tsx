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

export function AppShell() {
  const { m } = useI18n();

  return (
    <div className="flex min-h-dvh flex-col">
      {/* Desktop: floating glass bar with a capsule tab control. */}
      <header className="sticky top-0 z-20 hidden px-4 pt-4 md:block">
        <div className="glass mx-auto flex w-full max-w-5xl items-center justify-between rounded-full py-2 pr-2 pl-5">
          <Wordmark />
          <nav aria-label={m.nav.label}>
            <ul className="flex gap-1">
              {TABS.map(({ to, label, icon: Icon }) => (
                <li key={to}>
                  <NavLink
                    to={to}
                    end={to === '/'}
                    className={({ isActive }) =>
                      [
                        'flex min-h-10 items-center gap-2 rounded-full px-4 text-[0.95rem] font-semibold transition-colors',
                        isActive
                          ? 'bg-primary text-on-primary'
                          : 'text-ink-soft hover:bg-glass-pill hover:text-ink',
                      ].join(' ')
                    }
                  >
                    <Icon className="size-4.5" aria-hidden="true" />
                    <span>{m.nav[label]}</span>
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>

      <header className="px-4 pt-[max(1rem,env(safe-area-inset-top))] md:hidden">
        <Wordmark />
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pt-6 pb-32 md:pt-10 md:pb-12">
        <Outlet />
      </main>

      {/* Mobile: floating glass tab bar. */}
      <nav
        className="fixed inset-x-3 bottom-[max(0.75rem,env(safe-area-inset-bottom))] z-20 md:hidden"
        aria-label={m.nav.label}
      >
        <ul className="glass grid grid-cols-5 rounded-[1.75rem] p-1.5">
          {TABS.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                end={to === '/'}
                className={({ isActive }) =>
                  [
                    'flex flex-col items-center gap-0.5 rounded-[1.4rem] py-1.5 text-[0.66rem] font-semibold tracking-tight transition-colors',
                    isActive ? 'bg-glass-pill text-ink' : 'text-ink-soft',
                  ].join(' ')
                }
              >
                <Icon className="size-6" strokeWidth={1.8} aria-hidden="true" />
                <span className="max-w-full truncate">{m.nav[label]}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
