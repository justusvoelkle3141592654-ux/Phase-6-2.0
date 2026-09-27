import {
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type PointerEvent as ReactPointerEvent,
} from 'react';
import { Check, RotateCcw, X } from 'lucide-react';
import type { AnswerResult } from '@gero/shared';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { languageName } from '../lib/languages';
import { useAnswer, useOverride } from '../lib/learn';
import type { SessionItem } from '../lib/session';
import { Notice } from './Notice';
import { StageMeter } from './StageBadge';

const SWIPE_THRESHOLD = 110;
const AUTO_NEXT_MS = 1300;

type Phase = 'front' | 'checking' | 'back';

function canHover() {
  return typeof window !== 'undefined' && window.matchMedia?.('(pointer: fine)').matches;
}

/**
 * One flash card. Way A: type the answer, Enter → checked, card flips.
 * Way B: tap the card → flips → swipe right (knew it) or left (didn't).
 * Calls `onNext` with the final result when the user moves on.
 */
export function Flashcard({
  item,
  position,
  total,
  onNext,
}: {
  item: SessionItem;
  position: number;
  total: number;
  onNext: (result: AnswerResult) => void;
}) {
  const { m } = useI18n();
  const { card, direction } = item;
  const prompt = direction === 'foreign_native' ? card.word : card.translation;
  const solution = direction === 'foreign_native' ? card.translation : card.word;

  const [phase, setPhase] = useState<Phase>('front');
  const [typed, setTyped] = useState('');
  const [result, setResult] = useState<AnswerResult | null>(null);
  const [dx, setDx] = useState(0);
  const [dragging, setDragging] = useState(false);
  const [leaving, setLeaving] = useState<0 | 1 | -1>(0);
  const drag = useRef<{ startX: number; id: number } | null>(null);
  const shownAt = useRef(performance.now());
  const firstKeyAt = useRef<number | null>(null);
  const flippedAt = useRef<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const nextRef = useRef<HTMLButtonElement>(null);
  const answer = useAnswer();
  const override = useOverride();

  const selfGrading = phase === 'back' && result === null;
  const elapsed = (at: number | null) => (at === null ? null : Math.round(at - shownAt.current));

  useEffect(() => {
    if (canHover()) inputRef.current?.focus();
  }, []);

  // Correct typed answers move on automatically.
  useEffect(() => {
    if (!result?.correct || result.decidedBy === 'self') return;
    const timer = setTimeout(() => onNext(result), AUTO_NEXT_MS);
    return () => clearTimeout(timer);
  }, [result, onNext]);

  useEffect(() => {
    if (phase === 'back' && result && !result.correct) nextRef.current?.focus();
  }, [phase, result]);

  const submitTyped = (event: FormEvent) => {
    event.preventDefault();
    if (phase !== 'front') return;
    setPhase('checking');
    answer.mutate(
      {
        vocabId: card.vocabId,
        direction,
        answer: typed,
        msToFirstKey: elapsed(firstKeyAt.current),
        msTotal: elapsed(performance.now()),
        repeat: item.repeat,
      },
      {
        onSuccess: (res) => {
          setResult(res);
          setPhase('back');
        },
        onError: () => setPhase('front'),
      },
    );
  };

  const flip = () => {
    if (phase !== 'front') return;
    flippedAt.current = performance.now();
    setPhase('back');
  };

  const grade = (knew: boolean) => {
    if (!selfGrading || answer.isPending) return;
    setLeaving(knew ? 1 : -1);
    answer.mutate(
      {
        vocabId: card.vocabId,
        direction,
        answer: null,
        selfGrade: knew,
        msToFirstKey: null,
        msTotal: elapsed(flippedAt.current),
        repeat: item.repeat,
      },
      {
        onSuccess: (res) => onNext(res),
        onError: () => {
          setLeaving(0);
          setDx(0);
        },
      },
    );
  };

  // Arrow keys for self-assessment, Enter/Space to flip or continue.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return;
      if (selfGrading && event.key === 'ArrowRight') grade(true);
      else if (selfGrading && event.key === 'ArrowLeft') grade(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const onPointerDown = (event: ReactPointerEvent) => {
    if (!selfGrading) return;
    drag.current = { startX: event.clientX, id: event.pointerId };
    setDragging(true);
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  };
  const onPointerMove = (event: ReactPointerEvent) => {
    if (drag.current?.id === event.pointerId) setDx(event.clientX - drag.current.startX);
  };
  const onPointerUp = () => {
    if (!drag.current) return;
    drag.current = null;
    setDragging(false);
    if (Math.abs(dx) > SWIPE_THRESHOLD) grade(dx > 0);
    else setDx(0);
  };

  const offset = leaving ? leaving * 600 : dx;
  const tint = Math.min(Math.abs(offset) / 160, 1);
  const flipped = phase === 'back';

  return (
    <div className="mx-auto w-full max-w-xl">
      <div className="mb-3 flex items-center justify-between gap-3 text-sm text-ink-soft">
        <span className="truncate">
          {card.packageName} · {languageName(m, card.language)}
          {item.repeat && <span className="ml-2 font-semibold text-ink">· {m.learn.repeat}</span>}
        </span>
        <span className="shrink-0 tabular-nums">
          {fill(m.learn.progress, { n: position, total })}
        </span>
      </div>

      <div
        className="touch-pan-y select-none [perspective:1400px]"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        style={{
          transform: `translateX(${offset}px) rotate(${offset / 18}deg)`,
          transition: dragging ? 'none' : 'transform 250ms ease-out',
        }}
      >
        <div
          className="grid transition-transform duration-500 [transform-style:preserve-3d]"
          style={{ transform: flipped ? 'rotateY(180deg)' : 'none' }}
        >
          {/* Front */}
          <div
            className="panel relative flex min-h-80 flex-col [grid-area:1/1] [backface-visibility:hidden] sm:p-8"
            aria-hidden={flipped}
            inert={flipped}
          >
            <div className="flex items-center gap-2 text-sm font-semibold text-ink-soft">
              <StageMeter stage={card.stage} />
              {fill(m.packages.stage, { n: card.stage })}
            </div>
            <button
              type="button"
              onClick={flip}
              className="my-6 flex flex-1 items-center justify-center rounded-2xl px-2 text-center"
              aria-label={`${m.learn.flip}: ${prompt}`}
            >
              <span className="text-3xl font-bold tracking-tight break-words sm:text-4xl">
                {prompt}
              </span>
            </button>
            <form onSubmit={submitTyped} className="space-y-2">
              <label htmlFor="learn-answer" className="sr-only">
                {m.learn.answerLabel}
              </label>
              <div className="flex gap-2">
                <input
                  ref={inputRef}
                  id="learn-answer"
                  className="field"
                  autoComplete="off"
                  autoCapitalize="off"
                  autoCorrect="off"
                  spellCheck={false}
                  enterKeyHint="done"
                  placeholder={m.learn.answerPlaceholder}
                  value={typed}
                  disabled={phase !== 'front'}
                  onChange={(e) => {
                    if (firstKeyAt.current === null) firstKeyAt.current = performance.now();
                    setTyped(e.target.value);
                  }}
                />
                <button
                  type="submit"
                  className="btn btn-primary shrink-0"
                  disabled={phase !== 'front'}
                >
                  {phase === 'checking' ? m.learn.checking : m.learn.check}
                </button>
              </div>
              <p className="text-center text-sm text-ink-soft">{m.learn.flipHint}</p>
            </form>
          </div>

          {/* Back */}
          <div
            className="panel relative flex min-h-80 flex-col overflow-hidden [grid-area:1/1] [backface-visibility:hidden] [transform:rotateY(180deg)] sm:p-8"
            aria-hidden={!flipped}
            inert={!flipped}
            aria-live="polite"
          >
            <div
              className="pointer-events-none absolute inset-0 transition-opacity"
              style={{
                opacity: tint * 0.35,
                background:
                  offset > 0
                    ? 'var(--color-richtig)'
                    : offset < 0
                      ? 'var(--color-falsch)'
                      : 'transparent',
              }}
            />
            <p className="text-sm text-ink-soft">{prompt}</p>
            <div className="my-5 flex flex-1 flex-col items-center justify-center gap-2 text-center">
              <span className="sr-only">{m.learn.solution}:</span>
              <span className="text-3xl font-bold tracking-tight break-words sm:text-4xl">
                {solution}
              </span>
              {card.extra && <span className="text-lg text-ink-soft">{card.extra}</span>}
            </div>

            {result && (
              <div className="space-y-3">
                <div
                  className={[
                    'flex items-start gap-2.5 rounded-2xl px-4 py-3 font-semibold',
                    result.correct ? 'bg-richtig-soft text-richtig' : 'bg-falsch-soft text-falsch',
                  ].join(' ')}
                  role="status"
                >
                  {result.correct ? (
                    <Check className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
                  ) : (
                    <X className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
                  )}
                  <div>
                    <p>
                      {result.correct
                        ? result.typoOf
                          ? fill(m.learn.typo, { word: result.typoOf })
                          : m.learn.correct
                        : m.learn.wrong}
                    </p>
                    <p className="text-sm font-medium opacity-80">
                      {result.learned
                        ? m.learn.learnedNow
                        : fill(m.learn.stageChange, {
                            from: result.stageBefore,
                            to: result.stageAfter,
                          })}
                    </p>
                  </div>
                </div>
                {!result.correct && typed.trim() && (
                  <p className="text-sm text-ink-soft">
                    {m.learn.yourAnswer}: <span className="font-semibold text-ink">{typed}</span>
                  </p>
                )}
                {result.canOverride && (
                  <p className="text-sm text-ink-soft">
                    {result.aiFailed ? m.learn.aiFailed : m.learn.localDecision}
                  </p>
                )}
                {result.decidedBy === 'ai' && (
                  <p className="text-sm text-ink-soft">{m.learn.byAi}</p>
                )}
                <div className="flex flex-wrap justify-end gap-2">
                  {result.canOverride && (
                    <button
                      type="button"
                      className="btn btn-secondary"
                      disabled={override.isPending}
                      onClick={() => override.mutate(result.attemptId, { onSuccess: setResult })}
                    >
                      <RotateCcw className="size-4" aria-hidden="true" />
                      {m.learn.iWasRight}
                    </button>
                  )}
                  <button
                    ref={nextRef}
                    type="button"
                    className="btn btn-primary"
                    onClick={() => onNext(result)}
                  >
                    {m.learn.next}
                  </button>
                </div>
              </div>
            )}

            {selfGrading && (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    className="btn border border-falsch/40 bg-falsch-soft text-falsch hover:brightness-95"
                    onClick={() => grade(false)}
                    disabled={answer.isPending}
                  >
                    <X className="size-5" aria-hidden="true" />
                    {m.learn.didntKnow}
                  </button>
                  <button
                    type="button"
                    className="btn border border-richtig/40 bg-richtig-soft text-richtig hover:brightness-95"
                    onClick={() => grade(true)}
                    disabled={answer.isPending}
                  >
                    <Check className="size-5" aria-hidden="true" />
                    {m.learn.knew}
                  </button>
                </div>
                <p className="text-center text-sm text-ink-soft">{m.learn.swipeHint}</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {answer.isError && (
        <div className="mt-4">
          <Notice tone="error">{errorMessage(m, answer.error)}</Notice>
        </div>
      )}
      {override.isError && (
        <div className="mt-4">
          <Notice tone="error">{errorMessage(m, override.error)}</Notice>
        </div>
      )}
    </div>
  );
}
