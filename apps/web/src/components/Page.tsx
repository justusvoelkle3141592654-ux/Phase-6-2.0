import type { ReactNode } from 'react';

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
      <h1 className="font-word text-3xl font-bold tracking-tight sm:text-4xl">{title}</h1>
      {actions}
    </div>
  );
}

export function EmptyState({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-[var(--radius-card)] border-2 border-dashed border-rule px-6 py-12 text-center">
      <p className="mx-auto max-w-md text-ink-soft">{children}</p>
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}
