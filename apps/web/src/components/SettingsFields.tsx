import { useMemo } from 'react';
import { Check } from 'lucide-react';
import {
  AI_TIMEOUT_MAX_MS,
  AI_TIMEOUT_MIN_MS,
  AUTO_NEXT_MAX_MS,
  AUTO_NEXT_STEP_MS,
  DAILY_LIMIT_MAX,
  DEFAULT_AI_TIMEOUT_MS,
  INTERVAL_MAX_DAYS,
  WRONG_MODES,
  type UserSettings,
  type WrongMode,
} from '@wordflow/shared';
import { useI18n } from '../i18n';
import { fill } from '../lib/format';

/** Choice list in the style of an iOS grouped list: one row per option, check mark on the chosen one. */
export function ChoiceList<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: Array<{ value: T; label: string }>;
  value: T;
  onChange: (value: T) => void;
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="divide-y divide-rule overflow-hidden rounded-2xl bg-surface-2"
    >
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className="flex min-h-12 w-full items-center justify-between px-4 text-left font-medium transition-colors hover:bg-glass-pill"
        >
          {option.label}
          {value === option.value && <Check className="size-5" aria-hidden="true" />}
        </button>
      ))}
    </div>
  );
}

function allTimezones(current: string): string[] {
  let zones: string[] = [];
  try {
    zones = Intl.supportedValuesOf('timeZone');
  } catch {
    // Very old browsers: offer at least the current zone.
  }
  return zones.includes(current) ? zones : [current, ...zones];
}

export function TimezoneSelect({
  id,
  value,
  onChange,
}: {
  id: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const zones = useMemo(() => allTimezones(value), [value]);
  return (
    <select id={id} className="field" value={value} onChange={(e) => onChange(e.target.value)}>
      {zones.map((zone) => (
        <option key={zone} value={zone}>
          {zone.replaceAll('_', ' ')}
        </option>
      ))}
    </select>
  );
}

/** Range slider for a duration, with the current value in seconds next to the label. */
export function SecondsSlider({
  id,
  label,
  valueMs,
  minMs,
  maxMs,
  stepMs,
  offLabel,
  describedBy,
  onChange,
}: {
  id: string;
  label: string;
  valueMs: number;
  minMs: number;
  maxMs: number;
  stepMs: number;
  /** Shown instead of "0 seconds" when the slider is at 0. */
  offLabel?: string;
  describedBy?: string;
  onChange: (ms: number) => void;
}) {
  const { m, lang } = useI18n();
  const seconds = (ms: number) => (ms / 1000).toLocaleString(lang, { maximumFractionDigits: 1 });
  const shown = valueMs === 0 && offLabel ? offLabel : `${seconds(valueMs)} ${m.fields.seconds}`;
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between gap-3">
        <label htmlFor={id} className="font-semibold">
          {label}
        </label>
        <output htmlFor={id} className="font-semibold tabular-nums">
          {shown}
        </output>
      </div>
      <input
        id={id}
        type="range"
        className="h-6 w-full cursor-pointer accent-ink"
        min={minMs}
        max={maxMs}
        step={stepMs}
        value={valueMs}
        aria-valuetext={shown}
        aria-describedby={describedBy}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <div className="flex justify-between text-xs text-ink-soft tabular-nums" aria-hidden="true">
        <span>{minMs === 0 && offLabel ? offLabel : `${seconds(minMs)} s`}</span>
        <span>{seconds(maxMs)} s</span>
      </div>
    </div>
  );
}

/** Seconds as text (the setup wizard shows it in its summary); see `parseTimeout`. */
export function TimeoutField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const { m } = useI18n();
  return (
    <div>
      <div className="rounded-2xl bg-surface-2 px-4 py-3.5">
        <SecondsSlider
          id="ai-timeout"
          label={m.fields.timeout}
          valueMs={parseTimeout(value) ?? DEFAULT_AI_TIMEOUT_MS}
          minMs={AI_TIMEOUT_MIN_MS}
          maxMs={AI_TIMEOUT_MAX_MS}
          stepMs={500}
          describedBy="ai-timeout-hint"
          onChange={(ms) => onChange(String(ms / 1000))}
        />
      </div>
      <p id="ai-timeout-hint" className="hint">
        {m.fields.timeoutHint}
      </p>
    </div>
  );
}

export type AutoNextValues = Pick<
  UserSettings,
  'autoNextCorrectMs' | 'autoNextTypoMs' | 'autoNextWrongMs'
>;

/** One slider per outcome: how long the solution stays up before the next card. */
export function AutoNextFields({
  value,
  onChange,
}: {
  value: AutoNextValues;
  onChange: (value: AutoNextValues) => void;
}) {
  const { m } = useI18n();
  const rows: Array<{ key: keyof AutoNextValues; label: string }> = [
    { key: 'autoNextCorrectMs', label: m.fields.autoNextCorrect },
    { key: 'autoNextTypoMs', label: m.fields.autoNextTypo },
    { key: 'autoNextWrongMs', label: m.fields.autoNextWrong },
  ];
  return (
    <fieldset>
      <legend className="label">{m.fields.autoNextLegend}</legend>
      <div className="space-y-4 rounded-2xl bg-surface-2 px-4 py-3.5">
        {rows.map((row) => (
          <SecondsSlider
            key={row.key}
            id={`auto-next-${row.key}`}
            label={row.label}
            valueMs={value[row.key]}
            minMs={0}
            maxMs={AUTO_NEXT_MAX_MS}
            stepMs={AUTO_NEXT_STEP_MS}
            offLabel={m.fields.autoNextOff}
            describedBy="auto-next-hint"
            onChange={(ms) => onChange({ ...value, [row.key]: ms })}
          />
        ))}
      </div>
      <p id="auto-next-hint" className="hint">
        {m.fields.autoNextHint}
      </p>
    </fieldset>
  );
}

/** Most different words per day, as text while editing; see `parseDailyLimit`. */
export function DailyLimitField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const { m } = useI18n();
  return (
    <div>
      <label className="label" htmlFor="daily-limit">
        {m.fields.dailyLimit}
      </label>
      <input
        id="daily-limit"
        className="field w-32 text-right font-semibold tabular-nums"
        type="number"
        inputMode="numeric"
        min={1}
        max={DAILY_LIMIT_MAX}
        value={value}
        aria-describedby="daily-limit-hint"
        onChange={(e) => onChange(e.target.value)}
      />
      <p id="daily-limit-hint" className="hint">
        {m.fields.dailyLimitHint}
      </p>
    </div>
  );
}

export function parseDailyLimit(value: string): number | null {
  const n = Number(value);
  return Number.isInteger(n) && n >= 1 && n <= DAILY_LIMIT_MAX ? n : null;
}

export function parseTimeout(value: string): number | null {
  const ms = Math.round(Number(value.replace(',', '.')) * 1000);
  return Number.isFinite(ms) && ms >= AI_TIMEOUT_MIN_MS && ms <= AI_TIMEOUT_MAX_MS ? ms : null;
}

/** Five day counts for stages 2–6, as text while editing; see `parseIntervals`. */
export function IntervalsField({
  value,
  onChange,
}: {
  value: string[];
  onChange: (value: string[]) => void;
}) {
  const { m } = useI18n();
  return (
    <fieldset>
      <legend className="label">{m.fields.intervalsLegend}</legend>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-5">
        {value.map((days, i) => (
          <div
            key={i}
            className="flex items-center justify-between gap-3 rounded-xl bg-surface-2 px-3.5 py-1.5 sm:flex-col sm:items-stretch sm:gap-1 sm:py-2.5"
          >
            <label htmlFor={`interval-${i}`} className="text-sm font-semibold text-ink-soft">
              {fill(m.fields.stage, { n: i + 2 })}
            </label>
            <div className="flex items-center gap-2">
              <input
                id={`interval-${i}`}
                className="w-16 min-w-0 rounded-lg bg-surface px-2 py-1.5 text-right font-semibold tabular-nums outline-none focus:ring-1 focus:ring-ink sm:w-auto sm:flex-1"
                type="number"
                inputMode="numeric"
                min={1}
                max={INTERVAL_MAX_DAYS}
                value={days}
                onChange={(e) => onChange(value.map((v, j) => (j === i ? e.target.value : v)))}
              />
              <span className="text-sm text-ink-soft sm:hidden">{m.fields.days}</span>
            </div>
          </div>
        ))}
      </div>
      <p className="hint">{m.fields.intervalsHint}</p>
    </fieldset>
  );
}

export function parseIntervals(value: string[]): number[] | null {
  const days = value.map((v) => Number(v));
  return days.every((d) => Number.isInteger(d) && d >= 1 && d <= INTERVAL_MAX_DAYS) ? days : null;
}

export function WrongModeField({
  value,
  onChange,
}: {
  value: WrongMode;
  onChange: (value: WrongMode) => void;
}) {
  const { m } = useI18n();
  const labels: Record<WrongMode, string> = {
    reset: m.fields.wrongReset,
    back: m.fields.wrongBack,
  };
  return (
    <div>
      <span className="label">{m.fields.wrongMode}</span>
      <ChoiceList
        label={m.fields.wrongMode}
        options={WRONG_MODES.map((mode) => ({ value: mode, label: labels[mode] }))}
        value={value}
        onChange={onChange}
      />
    </div>
  );
}
