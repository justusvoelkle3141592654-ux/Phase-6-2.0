import { useCallback, useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { GraduationCap } from 'lucide-react';
import type { AnswerResult } from '@gero/shared';
import { Flashcard } from '../components/Flashcard';
import { Notice } from '../components/Notice';
import { EmptyState, PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { languageName } from '../lib/languages';
import { useDueCards, warmUp } from '../lib/learn';
import { usePackages } from '../lib/packages';
import {
  changeDirection,
  recordAnswer,
  sessionStats,
  startSession,
  type DirectionChoice,
  type SessionState,
} from '../lib/session';

const CHOICES: DirectionChoice[] = ['package', 'foreign_native', 'native_foreign', 'random'];

function DirectionSelect({
  value,
  onChange,
}: {
  value: DirectionChoice;
  onChange: (v: DirectionChoice) => void;
}) {
  const { m } = useI18n();
  return (
    <div className="flex min-w-0 items-center gap-2">
      <label htmlFor="learn-direction" className="shrink-0 text-sm font-semibold text-ink-soft">
        {m.learn.direction}
      </label>
      <select
        id="learn-direction"
        className="field min-h-9 w-auto min-w-0 py-1 text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value as DirectionChoice)}
      >
        {CHOICES.map((c) => (
          <option key={c} value={c}>
            {m.learn.directions[c]}
          </option>
        ))}
      </select>
    </div>
  );
}

/** Starts a session once the due cards have loaded. */
function SessionLoader({
  packageId,
  choice,
  onReady,
}: {
  packageId: number | null;
  choice: DirectionChoice;
  onReady: (state: SessionState) => void;
}) {
  const { m } = useI18n();
  const cards = useDueCards(packageId, true);
  const data = cards.data;
  useEffect(() => warmUp(), []);
  useEffect(() => {
    if (data) onReady(startSession(data, choice));
  }, [data, choice, onReady]);
  if (cards.isError) return <Notice tone="error">{errorMessage(m, cards.error)}</Notice>;
  return (
    <p role="status" className="text-ink-soft">
      {m.common.loading}
    </p>
  );
}

function Session({
  state,
  setState,
  onEnd,
}: {
  state: SessionState;
  setState: (updater: (s: SessionState) => SessionState) => void;
  onEnd: () => void;
}) {
  const { m } = useI18n();
  const item = state.items[state.index];
  const onNext = useCallback(
    (result: AnswerResult) => setState((s) => recordAnswer(s, result)),
    [setState],
  );

  if (!item) {
    const stats = sessionStats(state);
    return (
      <div className="panel mx-auto max-w-xl text-center sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight">{m.learn.doneTitle}</h1>
        <p className="mt-2 text-lg">
          {fill(m.learn.doneText, { correct: stats.correct, wrong: stats.wrong })}
        </p>
        {stats.repeatedCorrect > 0 && (
          <p className="mt-1 text-ink-soft">
            {fill(m.learn.doneRepeat, { n: stats.repeatedCorrect })}
          </p>
        )}
        {stats.learned > 0 && (
          <p className="mt-1 text-ink-soft">{fill(m.learn.doneLearned, { n: stats.learned })}</p>
        )}
        <button type="button" className="btn btn-primary mt-6" onClick={onEnd}>
          {m.learn.finish}
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="mx-auto mb-4 flex max-w-xl items-center justify-between gap-3">
        <DirectionSelect
          value={state.choice}
          onChange={(c) => setState((s) => changeDirection(s, c))}
        />
        <button type="button" className="btn btn-ghost min-h-9 shrink-0 px-3" onClick={onEnd}>
          {m.learn.cancel}
        </button>
      </div>
      <Flashcard
        key={`${state.index}-${item.card.vocabId}`}
        item={item}
        position={state.index + 1}
        total={state.items.length}
        onNext={onNext}
      />
    </>
  );
}

export function LearnPage() {
  const { m } = useI18n();
  const [params, setParams] = useSearchParams();
  const packages = usePackages();
  const [choice, setChoice] = useState<DirectionChoice>('package');
  const [session, setSession] = useState<SessionState | null>(null);
  const [starting, setStarting] = useState<{ packageId: number | null } | null>(() => {
    const id = Number(params.get('package'));
    return id ? { packageId: id } : null;
  });

  const setState = useCallback(
    (updater: (s: SessionState) => SessionState) => setSession((s) => (s ? updater(s) : s)),
    [],
  );
  const end = useCallback(() => {
    setSession(null);
    setStarting(null);
    setParams((p) => (p.has('package') ? {} : p), { replace: true });
  }, [setParams]);
  const onReady = useCallback(
    (s: SessionState) => {
      if (s.items.length === 0) return end();
      setStarting(null);
      setSession(s);
    },
    [end],
  );

  if (session) return <Session state={session} setState={setState} onEnd={end} />;
  if (starting) {
    return <SessionLoader packageId={starting.packageId} choice={choice} onReady={onReady} />;
  }

  const due = (packages.data ?? []).filter((p) => p.counts.due > 0);
  const total = due.reduce((sum, p) => sum + p.counts.due, 0);

  return (
    <>
      <PageHeader title={m.learn.title} />
      {packages.isPending ? (
        <p role="status" className="text-ink-soft">
          {m.common.loading}
        </p>
      ) : packages.isError ? (
        <Notice tone="error">{errorMessage(m, packages.error)}</Notice>
      ) : total === 0 ? (
        <EmptyState
          action={
            <Link to="/packages" className="btn btn-secondary">
              {m.nav.packages}
            </Link>
          }
        >
          {m.learn.empty} {m.learn.emptyHint}
        </EmptyState>
      ) : (
        <div className="grid max-w-xl gap-4">
          <section className="panel">
            <p className="text-3xl font-bold tracking-tight tabular-nums">
              {fill(m.learn.dueTotal, { n: total })}
            </p>
            <div className="mt-4 flex flex-wrap items-center gap-3">
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setStarting({ packageId: null })}
              >
                <GraduationCap className="size-5" aria-hidden="true" />
                {m.learn.startAll}
              </button>
              <DirectionSelect value={choice} onChange={setChoice} />
            </div>
          </section>
          <ul className="divide-y divide-rule overflow-hidden rounded-[var(--radius-card)] bg-surface shadow-[var(--shadow-card)]">
            {due.map((p) => (
              <li key={p.id} className="flex items-center gap-4 px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{p.name}</p>
                  <p className="text-sm text-ink-soft">
                    {languageName(m, p.language)} · {fill(m.packages.due, { n: p.counts.due })}
                  </p>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary min-h-9"
                  onClick={() => setStarting({ packageId: p.id })}
                >
                  {m.learn.start}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}
