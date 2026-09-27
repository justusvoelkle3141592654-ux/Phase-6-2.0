import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Check, Info } from 'lucide-react';
import {
  DEFAULT_AI_PROVIDER,
  DEFAULT_TEXT_MODEL,
  UI_LANGUAGES,
  type UserDto,
  type UserSettings,
  type WrongMode,
} from '@gero/shared';
import { CenteredLayout } from '../components/CenteredLayout';
import { Notice } from '../components/Notice';
import {
  ChoiceList,
  IntervalsField,
  parseIntervals,
  parseTimeout,
  TimeoutField,
  TimezoneSelect,
  WrongModeField,
} from '../components/SettingsFields';
import { useI18n } from '../i18n';
import { useUpdateProfile } from '../lib/auth';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { useCompleteSetup, useSettings, useUpdateSettings } from '../lib/settings';

const LANGUAGE_NAMES = { de: 'Deutsch', en: 'English' } as const;

interface Draft {
  name: string;
  timezone: string;
  timeout: string;
  intervals: string[];
  wrongMode: WrongMode;
}

function stepKey(userId: number) {
  return `gero.setupStep.${userId}`;
}

function readStep(userId: number, total: number): number {
  try {
    const n = Number(localStorage.getItem(stepKey(userId)));
    return Number.isInteger(n) && n >= 0 && n < total ? n : 0;
  } catch {
    return 0;
  }
}

function writeStep(userId: number, step: number | null) {
  try {
    if (step === null) localStorage.removeItem(stepKey(userId));
    else localStorage.setItem(stepKey(userId), String(step));
  } catch {
    // The step is a convenience only.
  }
}

/** Read-only row for values that cannot be changed yet. */
function ValueRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex min-h-12 flex-wrap items-center justify-between gap-x-4 gap-y-0.5 px-4 py-2.5">
      <span className="text-ink-soft">{label}</span>
      <span className="ml-auto text-right font-semibold wrap-break-word">{value}</span>
    </div>
  );
}

function ValueList({ children }: { children: ReactNode }) {
  return (
    <div className="divide-y divide-rule overflow-hidden rounded-2xl bg-surface-2">{children}</div>
  );
}

function LaterNote({ children }: { children?: ReactNode }) {
  const { m } = useI18n();
  return (
    <div className="flex gap-3 rounded-2xl border border-rule px-4 py-3 text-sm text-ink-soft">
      <Info className="mt-0.5 size-4.5 shrink-0" aria-hidden="true" />
      <div className="space-y-1">
        {children && <p>{children}</p>}
        <p>{m.setup.later}</p>
      </div>
    </div>
  );
}

interface Step {
  title: string;
  text: string;
  body: ReactNode;
  /** Saves the step; resolves when done. */
  save?: () => Promise<unknown>;
  valid?: boolean;
}

function WizardSteps({ user, settings }: { user: UserDto; settings: UserSettings }) {
  const { m, lang, setLang } = useI18n();
  const updateProfile = useUpdateProfile();
  const updateSettings = useUpdateSettings();
  const complete = useCompleteSetup();

  const [draft, setDraft] = useState<Draft>(() => ({
    name: user.name,
    timezone: user.timezone,
    timeout: String(settings.aiTimeoutMs / 1000),
    intervals: settings.intervals.map(String),
    wrongMode: settings.wrongMode,
  }));
  const set = <K extends keyof Draft>(key: K, value: Draft[K]) =>
    setDraft((d) => ({ ...d, [key]: value }));

  const timeoutMs = parseTimeout(draft.timeout);
  const intervals = parseIntervals(draft.intervals);

  const steps: Step[] = [
    {
      ...m.setup.language,
      body: (
        <ChoiceList
          label={m.fields.language}
          options={UI_LANGUAGES.map((code) => ({ value: code, label: LANGUAGE_NAMES[code] }))}
          value={lang}
          onChange={(code) => {
            setLang(code);
            updateProfile.mutate({ uiLanguage: code });
          }}
        />
      ),
      save: () => updateProfile.mutateAsync({ uiLanguage: lang }),
    },
    {
      ...m.setup.name,
      body: (
        <div>
          <label className="label" htmlFor="setup-name">
            {m.fields.name}
          </label>
          <input
            id="setup-name"
            className="field"
            autoComplete="nickname"
            maxLength={80}
            placeholder={m.setup.name.placeholder}
            value={draft.name}
            onChange={(e) => set('name', e.target.value)}
          />
        </div>
      ),
      save: () => updateProfile.mutateAsync({ name: draft.name }),
    },
    {
      ...m.setup.timezone,
      body: (
        <div>
          <label className="label" htmlFor="setup-timezone">
            {m.fields.timezone}
          </label>
          <TimezoneSelect
            id="setup-timezone"
            value={draft.timezone}
            onChange={(v) => set('timezone', v)}
          />
        </div>
      ),
      save: () => updateProfile.mutateAsync({ timezone: draft.timezone }),
    },
    {
      title: m.setup.checkProvider.title,
      text: m.setup.checkProvider.text,
      body: (
        <div className="space-y-4">
          <ValueList>
            <div className="flex min-h-12 items-center justify-between px-4">
              <span className="font-semibold">{DEFAULT_AI_PROVIDER}</span>
              <span className="flex items-center gap-1.5 text-sm text-ink-soft">
                <Check className="size-4.5" aria-hidden="true" />
                {m.setup.checkProvider.preset}
              </span>
            </div>
          </ValueList>
          <LaterNote>{m.setup.checkProvider.others}</LaterNote>
        </div>
      ),
    },
    {
      title: m.setup.checkModel.title,
      text: m.setup.checkModel.text,
      body: (
        <div className="space-y-4">
          <ValueList>
            <ValueRow label={m.fields.provider} value={DEFAULT_AI_PROVIDER} />
            <ValueRow label={m.setup.checkModel.model} value={DEFAULT_TEXT_MODEL} />
          </ValueList>
          <button type="button" className="btn btn-secondary" disabled>
            {m.setup.checkModel.test}
          </button>
          <LaterNote />
        </div>
      ),
    },
    {
      title: m.setup.summary.title,
      text: m.setup.summary.text,
      body: (
        <div className="space-y-4">
          <ValueList>
            <ValueRow label={m.fields.provider} value={DEFAULT_AI_PROVIDER} />
            <ValueRow label={m.setup.checkModel.model} value={DEFAULT_TEXT_MODEL} />
          </ValueList>
          <LaterNote />
        </div>
      ),
    },
    {
      title: m.setup.vision.title,
      text: m.setup.vision.text,
      body: (
        <div className="space-y-4">
          <ValueList>
            <ValueRow label={m.fields.visionModel} value={m.setup.vision.none} />
          </ValueList>
          <LaterNote />
        </div>
      ),
    },
    {
      ...m.setup.timeout,
      body: <TimeoutField value={draft.timeout} onChange={(v) => set('timeout', v)} />,
      valid: timeoutMs !== null,
      save: () => updateSettings.mutateAsync({ aiTimeoutMs: timeoutMs! }),
    },
    {
      ...m.setup.learning,
      body: (
        <div className="space-y-6">
          <IntervalsField value={draft.intervals} onChange={(v) => set('intervals', v)} />
          <WrongModeField value={draft.wrongMode} onChange={(v) => set('wrongMode', v)} />
        </div>
      ),
      valid: intervals !== null,
      save: () => updateSettings.mutateAsync({ intervals: intervals!, wrongMode: draft.wrongMode }),
    },
    {
      ...m.setup.done,
      body: (
        <ValueList>
          <ValueRow label={m.fields.language} value={LANGUAGE_NAMES[lang]} />
          <ValueRow label={m.fields.name} value={draft.name.trim() || m.fields.noName} />
          <ValueRow label={m.fields.timezone} value={draft.timezone.replaceAll('_', ' ')} />
          <ValueRow label={m.fields.textModel} value={DEFAULT_TEXT_MODEL} />
          <ValueRow label={m.fields.visionModel} value={m.setup.vision.none} />
          <ValueRow label={m.fields.timeout} value={`${draft.timeout} ${m.fields.seconds}`} />
          <ValueRow
            label={m.fields.intervals}
            value={`${draft.intervals.join(' · ')} ${m.fields.days}`}
          />
          <ValueRow
            label={m.fields.wrongMode}
            value={draft.wrongMode === 'reset' ? m.fields.wrongReset : m.fields.wrongBack}
          />
        </ValueList>
      ),
      save: () => complete.mutateAsync(),
    },
  ];

  const total = steps.length;
  const [index, setIndex] = useState(() => readStep(user.id, total));
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);
  const step = steps[index]!;
  const isLast = index === total - 1;

  useEffect(() => {
    writeStep(user.id, index);
  }, [user.id, index]);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (step.valid === false) return;
    setError(null);
    setSaving(true);
    try {
      await step.save?.();
      if (isLast) writeStep(user.id, null);
      else setIndex(index + 1);
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={onSubmit} noValidate>
      <div className="mb-4 flex items-center justify-between gap-4">
        <p className="text-sm font-semibold text-ink-soft tabular-nums">
          {fill(m.setup.stepOf, { n: index + 1, total })}
        </p>
      </div>
      <div
        className="mb-6 flex gap-1"
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={total}
        aria-valuenow={index + 1}
        aria-label={fill(m.setup.stepOf, { n: index + 1, total })}
      >
        {steps.map((_, i) => (
          <span
            key={i}
            className={[
              'h-1 flex-1 rounded-full transition-colors',
              i <= index ? 'bg-primary' : 'bg-rule',
            ].join(' ')}
          />
        ))}
      </div>

      <div className="panel sm:p-8">
        <h1 className="text-2xl font-bold tracking-tight">{step.title}</h1>
        <p className="mt-1.5 text-ink-soft">{step.text}</p>
        <div className="mt-6">{step.body}</div>
        {error !== null && (
          <div className="mt-4">
            <Notice tone="error">{errorMessage(m, error)}</Notice>
          </div>
        )}
      </div>

      <div className="mt-6 flex items-center justify-between gap-3">
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            setError(null);
            setIndex(index - 1);
          }}
          disabled={index === 0 || saving}
        >
          {m.setup.back}
        </button>
        <button
          type="submit"
          className="btn btn-primary min-w-32"
          disabled={saving || step.valid === false}
        >
          {isLast ? m.setup.finish : m.setup.next}
        </button>
      </div>
    </form>
  );
}

/** Ten-step setup shown right after registration, until `setupCompleted`. */
export function SetupWizard({ user }: { user: UserDto }) {
  const { m } = useI18n();
  const settings = useSettings();
  return (
    <CenteredLayout width="max-w-xl" showLanguage={false}>
      {settings.data ? (
        <WizardSteps user={user} settings={settings.data} />
      ) : settings.isError ? (
        <Notice tone="error">{errorMessage(m, settings.error)}</Notice>
      ) : (
        <p role="status" className="text-center text-ink-soft">
          {m.common.loading}
        </p>
      )}
    </CenteredLayout>
  );
}
