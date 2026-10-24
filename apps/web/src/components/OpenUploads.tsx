import { Link } from 'react-router';
import { ChevronRight } from 'lucide-react';
import { useI18n } from '../i18n';
import { fill } from '../lib/format';
import { useOpenUploads } from '../lib/uploads';

/** Superuser: recognitions whose words have not been saved into a package yet. */
export function OpenUploads() {
  const { m, lang } = useI18n();
  const open = useOpenUploads();
  if (!open.data || open.data.length === 0) return null;
  const dateFormat = new Intl.DateTimeFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
  return (
    <section className="mt-8" aria-labelledby="open-title">
      <h2 id="open-title" className="mb-3 text-lg font-bold">
        {m.upload.open}
      </h2>
      <ul className="divide-y divide-rule overflow-hidden rounded-[var(--radius-card)] bg-surface shadow-[var(--shadow-card)]">
        {open.data.map((job) => (
          <li key={job.id}>
            <Link
              to={`/upload/${job.id}`}
              className="flex items-center gap-4 px-5 py-3.5 hover:bg-surface-2"
            >
              <span className="flex-1">
                {fill(m.upload.openItem, {
                  n: job.pages.length,
                  date: dateFormat.format(new Date(job.createdAt)),
                })}
              </span>
              <span className="text-sm font-semibold text-ink-soft">{m.upload.review}</span>
              <ChevronRight className="size-5 text-ink-soft" aria-hidden="true" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
