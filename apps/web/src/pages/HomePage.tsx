import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { Camera, GraduationCap, Sparkles } from 'lucide-react';
import type { OverviewDto } from '@vokabeltrainer/shared';
import { Notice } from '../components/Notice';
import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { useMe } from '../lib/auth';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { useOverview, useSummary } from '../lib/overview';

const STAGE_BG = [
  'bg-stufe-1',
  'bg-stufe-2',
  'bg-stufe-3',
  'bg-stufe-4',
  'bg-stufe-5',
  'bg-stufe-6',
];

function Stages({ data }: { data: OverviewDto }) {
  const { m } = useI18n();
  const max = Math.max(1, ...data.stages, data.learned);
  const bars = [
    ...data.stages.map((n, i) => ({
      label: fill(m.home.stage, { n: i + 1 }),
      short: String(i + 1),
      n,
      bg: STAGE_BG[i]!,
    })),
    { label: m.home.learned, short: '✓', n: data.learned, bg: 'bg-richtig' },
  ];
  return (
    <section className="panel" aria-labelledby="stages-title">
      <h2 id="stages-title" className="mb-4 text-lg font-bold">
        {m.home.stages}
      </h2>
      <ul className="flex h-40 items-end gap-2 sm:gap-3">
        {bars.map((b) => (
          <li
            key={b.label}
            className="flex h-full flex-1 flex-col items-center justify-end gap-1.5"
            aria-label={`${b.label}: ${b.n}`}
          >
            <span className="text-sm font-semibold tabular-nums">{b.n}</span>
            <span
              className={`w-full rounded-lg ${b.bg} ${b.n === 0 ? 'opacity-30' : ''}`}
              style={{ height: `${Math.max(4, (b.n / max) * 100)}%` }}
            />
            <span className="text-xs font-semibold text-ink-soft" aria-hidden="true">
              {b.short}
            </span>
          </li>
        ))}
      </ul>
      {data.inactive > 0 && (
        <p className="mt-3 text-sm text-ink-soft">
          {m.home.inactive}: {data.inactive}
        </p>
      )}
    </section>
  );
}

function TodayCard({ data }: { data: OverviewDto }) {
  const { m } = useI18n();
  const s = data.todayStats;
  if (s.attempts === 0) return null;
  const { lang } = useI18n();
  const seconds = (ms: number | null) =>
    ms === null
      ? '–'
      : `${(ms / 1000).toLocaleString(lang === 'de' ? 'de-DE' : 'en-GB', { maximumFractionDigits: 1 })} s`;
  return (
    <section className="panel" aria-labelledby="today-title">
      <h2 id="today-title" className="mb-4 text-lg font-bold">
        {m.home.today}
      </h2>
      <dl className="grid grid-cols-3 gap-3 text-center">
        <div className="rounded-2xl bg-surface-2 px-2 py-3">
          <dt className="text-xs font-semibold text-ink-soft">{m.home.attempts}</dt>
          <dd className="text-2xl font-bold tabular-nums">{s.attempts}</dd>
        </div>
        <div className="rounded-2xl bg-surface-2 px-2 py-3">
          <dt className="text-xs font-semibold text-ink-soft">{m.home.accuracy}</dt>
          <dd className="text-2xl font-bold tabular-nums">
            {s.accuracy === null ? '–' : `${Math.round(s.accuracy * 100)} %`}
          </dd>
        </div>
        <div className="rounded-2xl bg-surface-2 px-2 py-3">
          <dt className="text-xs font-semibold text-ink-soft">{m.home.speed}</dt>
          <dd className="text-2xl font-bold tabular-nums">{seconds(s.medianMsTotal)}</dd>
        </div>
      </dl>
      {s.difficult.length > 0 && (
        <div className="mt-4">
          <h3 className="mb-2 text-sm font-semibold text-ink-soft">{m.home.difficult}</h3>
          <ul className="divide-y divide-rule rounded-2xl bg-surface-2">
            {s.difficult.map((d) => (
              <li key={d.vocabId} className="flex items-center justify-between gap-3 px-4 py-2.5">
                <span className="min-w-0">
                  <span className="font-semibold">{d.word}</span>{' '}
                  <span className="text-ink-soft">– {d.translation}</span>
                </span>
                <span className="shrink-0 text-sm text-falsch">
                  {fill(m.home.wrongTimes, { n: d.wrong })}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function SummaryCard({ attempts }: { attempts: number }) {
  const { m } = useI18n();
  const summary = useSummary(attempts);
  const data = summary.data;
  let body: ReactNode;
  if (attempts === 0) body = <p className="text-ink-soft">{m.home.summaryNoAttempts}</p>;
  else if (summary.isPending)
    body = <p className="animate-pulse text-ink-soft">{m.home.summaryLoading}</p>;
  else if (summary.isError) body = <Notice tone="error">{errorMessage(m, summary.error)}</Notice>;
  else if (data?.text) {
    body = (
      <>
        <p className="leading-relaxed whitespace-pre-line">{data.text}</p>
        {data.stale && <p className="mt-2 text-sm text-ink-soft">{m.home.summaryStale}</p>}
      </>
    );
  } else {
    body = (
      <p className="text-ink-soft">
        {data?.reason === 'no_model'
          ? m.home.summaryNoModel
          : data?.reason === 'failed'
            ? m.home.summaryFailed
            : m.home.summaryNoAttempts}
      </p>
    );
  }
  const canRetry = data?.reason === 'failed' || summary.isError;
  return (
    <section className="panel" aria-labelledby="summary-title" aria-busy={summary.isFetching}>
      <h2 id="summary-title" className="mb-3 flex items-center gap-2 text-lg font-bold">
        <Sparkles className="size-5" aria-hidden="true" />
        {m.home.summary}
      </h2>
      {body}
      {canRetry && (
        <button
          type="button"
          className="btn btn-secondary mt-3"
          onClick={() => void summary.refetch()}
          disabled={summary.isFetching}
        >
          {m.home.retry}
        </button>
      )}
    </section>
  );
}

export function HomePage() {
  const { m, lang } = useI18n();
  const me = useMe().data;
  const overview = useOverview();

  const title = me?.name ? fill(m.home.hello, { name: me.name }) : m.home.title;
  if (overview.isPending) {
    return (
      <>
        <PageHeader title={title} />
        <p className="text-ink-soft">{m.common.loading}</p>
      </>
    );
  }
  if (overview.isError) {
    return (
      <>
        <PageHeader title={title} />
        <Notice tone="error">{errorMessage(m, overview.error)}</Notice>
      </>
    );
  }
  const data = overview.data;
  const total = data.stages.reduce((a, b) => a + b, 0) + data.learned + data.inactive;
  const date = (day: string) =>
    new Intl.DateTimeFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
      dateStyle: 'full',
      timeZone: 'UTC',
    }).format(new Date(`${day}T00:00:00Z`));

  if (total === 0) {
    return (
      <>
        <PageHeader title={title} />
        <EmptyState
          action={
            <Link to="/upload" className="btn btn-primary">
              <Camera className="size-5" aria-hidden="true" />
              {m.home.upload}
            </Link>
          }
        >
          {m.home.empty}
        </EmptyState>
      </>
    );
  }

  return (
    <>
      <PageHeader title={title} />
      <div className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-2">
        <section
          className="panel flex flex-col justify-between gap-4 bg-primary text-on-primary lg:col-span-2"
          aria-live="polite"
        >
          <div>
            <p className="text-3xl font-bold tracking-tight tabular-nums sm:text-4xl">
              {data.due === 0
                ? m.home.nothingDue
                : data.due === 1
                  ? m.home.dueOne
                  : fill(m.home.due, { n: data.due })}
            </p>
            {data.due === 0 && data.nextDue && (
              <p className="mt-2 opacity-80">
                {fill(m.home.nextDue, { date: date(data.nextDue) })}
              </p>
            )}
          </div>
          {data.due > 0 && (
            <Link
              to="/learn"
              className="btn self-start bg-on-primary text-primary hover:opacity-90"
            >
              <GraduationCap className="size-5" aria-hidden="true" />
              {m.home.learnNow}
            </Link>
          )}
        </section>
        <SummaryCard attempts={data.todayStats.attempts} />
        <Stages data={data} />
        <TodayCard data={data} />
      </div>
    </>
  );
}
