import { useState, type FormEvent } from 'react';
import {
  DIRECTIONS,
  LANGUAGE_PRESETS,
  type Direction,
  type PackageInput,
} from '@vokabeltrainer/shared';
import { useI18n } from '../i18n';
import { isPreset } from '../lib/languages';

const OTHER = '__other__';

export function PackageForm({
  initial,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
}: {
  initial?: { name: string; language: string; direction: Direction };
  submitLabel: string;
  pending?: boolean;
  onSubmit: (input: PackageInput) => void;
  onCancel?: () => void;
}) {
  const { m } = useI18n();
  const [name, setName] = useState(initial?.name ?? '');
  const [preset, setPreset] = useState<string>(
    initial ? (isPreset(initial.language) ? initial.language : OTHER) : 'en',
  );
  const [other, setOther] = useState(
    initial && !isPreset(initial.language) ? initial.language : '',
  );
  const [direction, setDirection] = useState<Direction>(initial?.direction ?? 'foreign_native');
  const language = preset === OTHER ? other.trim() : preset;
  const valid = name.trim() !== '' && language !== '';

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (valid) onSubmit({ name: name.trim(), language, direction });
  };

  return (
    <form className="space-y-4" onSubmit={submit}>
      <div>
        <label className="label" htmlFor="pkg-name">
          {m.packages.name}
        </label>
        <input
          id="pkg-name"
          className="field"
          maxLength={100}
          placeholder={m.packages.namePlaceholder}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor="pkg-language">
            {m.packages.language}
          </label>
          <select
            id="pkg-language"
            className="field"
            value={preset}
            onChange={(e) => setPreset(e.target.value)}
          >
            {LANGUAGE_PRESETS.map((code) => (
              <option key={code} value={code}>
                {m.packages.languages[code]}
              </option>
            ))}
            <option value={OTHER}>{m.packages.otherLanguage}</option>
          </select>
        </div>
        <div>
          <label className="label" htmlFor="pkg-direction">
            {m.packages.direction}
          </label>
          <select
            id="pkg-direction"
            className="field"
            value={direction}
            onChange={(e) => setDirection(e.target.value as Direction)}
          >
            {DIRECTIONS.map((d) => (
              <option key={d} value={d}>
                {m.packages.directions[d]}
              </option>
            ))}
          </select>
        </div>
      </div>
      {preset === OTHER && (
        <div>
          <label className="label" htmlFor="pkg-other">
            {m.packages.otherLanguage}
          </label>
          <input
            id="pkg-other"
            className="field"
            maxLength={40}
            placeholder={m.packages.otherLanguagePlaceholder}
            value={other}
            onChange={(e) => setOther(e.target.value)}
          />
        </div>
      )}
      <div className="flex flex-wrap gap-2 pt-1">
        <button type="submit" className="btn btn-primary" disabled={!valid || pending}>
          {submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-ghost" onClick={onCancel}>
            {m.packages.cancel}
          </button>
        )}
      </div>
    </form>
  );
}
