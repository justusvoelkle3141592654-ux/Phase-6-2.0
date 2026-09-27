import { useMemo } from 'react';
import { Check } from 'lucide-react';
import {
  AI_TIMEOUT_MAX_MS,
  AI_TIMEOUT_MIN_MS,
  INTERVAL_MAX_DAYS,
  WRONG_MODES,
  type WrongMode,
} from '@gero/shared';
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

/** Seconds as text so the field can be edited freely; see `parseTimeout`. */
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
      <label className="label" htmlFor="ai-timeout">
        {m.fields.timeout}
      </label>
      <div className="flex items-center gap-3">
        <input
          id="ai-timeout"
          className="field w-28 tabular-nums"
          type="number"
          inputMode="decimal"
          min={AI_TIMEOUT_MIN_MS / 1000}
          max={AI_TIMEOUT_MAX_MS / 1000}
          step={0.5}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby="ai-timeout-hint"
        />
        <span className="text-ink-soft">{m.fields.seconds}</span>
      </div>
      <p id="ai-timeout-hint" className="hint">
        {m.fields.timeoutHint}
      </p>
    </div>
  );
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
