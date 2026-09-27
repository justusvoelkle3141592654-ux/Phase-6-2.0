import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ChevronRight, Plus } from 'lucide-react';
import { Notice } from '../components/Notice';
import { PackageForm } from '../components/PackageForm';
import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { languageName } from '../lib/languages';
import { useCreatePackage, usePackages } from '../lib/packages';

export function PackagesPage() {
  const { m } = useI18n();
  const packages = usePackages();
  const create = useCreatePackage();
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title={m.packages.title}
        actions={
          !creating && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              <Plus className="size-4.5" aria-hidden="true" />
              {m.packages.create}
            </button>
          )
        }
      />

      {creating && (
        <section className="panel mb-6" aria-labelledby="create-title">
          <h2 id="create-title" className="mb-4 text-lg font-bold">
            {m.packages.createTitle}
          </h2>
          <PackageForm
            submitLabel={m.packages.create}
            pending={create.isPending}
            onCancel={() => setCreating(false)}
            onSubmit={(input) =>
              create.mutate(input, { onSuccess: (pkg) => void navigate(`/packages/${pkg.id}`) })
            }
          />
          {create.isError && (
            <div className="mt-4">
              <Notice tone="error">{errorMessage(m, create.error)}</Notice>
            </div>
          )}
        </section>
      )}

      {packages.isPending ? (
        <p role="status" className="text-ink-soft">
          {m.common.loading}
        </p>
      ) : packages.isError ? (
        <Notice tone="error">{errorMessage(m, packages.error)}</Notice>
      ) : packages.data.length === 0 ? (
        !creating && <EmptyState>{m.packages.empty}</EmptyState>
      ) : (
        <ul className="divide-y divide-rule overflow-hidden rounded-[var(--radius-card)] bg-surface shadow-[var(--shadow-card)]">
          {packages.data.map((pkg) => (
            <li key={pkg.id}>
              <Link
                to={`/packages/${pkg.id}`}
                className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-surface-2"
              >
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{pkg.name}</p>
                  <p className="mt-0.5 text-sm text-ink-soft">
                    {languageName(m, pkg.language)} ·{' '}
                    {fill(m.packages.words, { n: pkg.counts.total })}
                    {pkg.counts.inactive > 0 &&
                      ` · ${fill(m.packages.inactiveCount, { n: pkg.counts.inactive })}`}
                  </p>
                </div>
                {pkg.counts.due > 0 && (
                  <span className="rounded-full bg-primary px-2.5 py-0.5 text-xs font-semibold text-on-primary tabular-nums">
                    {fill(m.packages.due, { n: pkg.counts.due })}
                  </span>
                )}
                <ChevronRight className="size-5 text-ink-soft" aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
