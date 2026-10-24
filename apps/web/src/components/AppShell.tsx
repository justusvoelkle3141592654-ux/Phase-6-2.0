import { Link, NavLink, Outlet } from 'react-router';
import {
  BookOpen,
  GraduationCap,
  House,
  Layers,
  LogOut,
  Settings,
  type LucideIcon,
} from 'lucide-react';
import { useI18n } from '../i18n';
import type { Messages } from '../i18n/de';
import { useLogout } from '../lib/auth';
import { Wordmark } from './Wordmark';

interface Tab {
  to: string;
  label: keyof Messages['nav'];
  icon: LucideIcon;
}

export const TABS: Tab[] = [
  { to: '/', label: 'home', icon: House },
  { to: '/packages', label: 'packages', icon: Layers },
  { to: '/learn', label: 'learn', icon: GraduationCap },
  { to: '/declensions', label: 'declensions', icon: BookOpen },
  { to: '/settings', label: 'settings', icon: Settings },
];

/** Logo and app name; leads to the home page. */
function HomeLink() {
  const { m } = useI18n();
  return (
    <Link to="/" aria-label={m.nav.homeLink} className="rounded-full">
      <Wordmark />
    </Link>
  );
}

function LogoutButton({ className }: { className: string }) {
  const { m } = useI18n();
  const logout = useLogout();
  return (
    <button
      type="button"
      className={className}
      onClick={() => logout.mutate()}
      disabled={logout.isPending}
      aria-label={m.nav.logout}
      title={m.nav.logout}
    >
      <LogOut className="size-4.5" aria-hidden="true" />
    </button>
  );
}

export function AppShell() {
  const { m } = useI18n();

  return (
    <div className="flex min-h-dvh flex-col">
      {/* Desktop: floating glass bar with a capsule tab control. */}
      <header className="sticky top-0 z-20 hidden px-4 pt-4 md:block">
        <div className="glass mx-auto flex w-full max-w-5xl items-center justify-between rounded-full py-2 pr-2 pl-5">
          <HomeLink />
          <div className="flex items-center gap-1">
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
            <LogoutButton className="grid size-10 place-items-center rounded-full text-ink-soft transition-colors hover:bg-glass-pill hover:text-ink" />
          </div>
        </div>
      </header>

      <header className="flex items-center justify-between px-4 pt-[max(1rem,env(safe-area-inset-top))] md:hidden">
        <HomeLink />
        <LogoutButton className="glass grid size-10 place-items-center rounded-full text-ink-soft" />
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
