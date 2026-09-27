import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ChevronLeft, GraduationCap, Pencil, Trash2 } from 'lucide-react';
import type { PackageDto, VocabDto } from '@gero/shared';
import { Notice } from '../components/Notice';
import { PackageForm } from '../components/PackageForm';
import { StageBadge } from '../components/StageBadge';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { languageName } from '../lib/languages';
import {
  useActivatePackage,
  useActivateVocab,
  useAddVocab,
  useDeletePackage,
  useDeleteVocab,
  usePackage,
  useUpdatePackage,
  useUpdateVocab,
} from '../lib/packages';
import { PackagePhotos } from '../components/PackagePhotos';

interface VocabFields {
  word: string;
  extra: string;
  translation: string;
}

const EMPTY: VocabFields = { word: '', extra: '', translation: '' };

/** Word / extra / translation inputs, used for adding and editing. */
function VocabForm({
  idPrefix,
  initial = EMPTY,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
  showHints,
}: {
  idPrefix: string;
  initial?: VocabFields;
  submitLabel: string;
  pending?: boolean;
  onSubmit: (fields: VocabFields, reset: () => void) => void;
  onCancel?: () => void;
  showHints?: boolean;
}) {
  const { m } = useI18n();
  const [fields, setFields] = useState(initial);
  const valid = fields.word.trim() !== '' && fields.translation.trim() !== '';
  const set = (key: keyof VocabFields) => (e: { target: { value: string } }) =>
    setFields((f) => ({ ...f, [key]: e.target.value }));

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (valid) onSubmit(fields, () => setFields(EMPTY));
  };

  return (
    <form className="space-y-3" onSubmit={submit}>
      <div className="grid gap-3 sm:grid-cols-3">
        <div>
          <label className="label" htmlFor={`${idPrefix}-word`}>
            {m.packages.word}
          </label>
          <input
            id={`${idPrefix}-word`}
            className="field"
            maxLength={200}
            value={fields.word}
            onChange={set('word')}
          />
        </div>
        <div>
          <label className="label" htmlFor={`${idPrefix}-extra`}>
            {m.packages.extra}
          </label>
          <input
            id={`${idPrefix}-extra`}
            className="field"
            maxLength={300}
            value={fields.extra}
            onChange={set('extra')}
          />
        </div>
        <div>
          <label className="label" htmlFor={`${idPrefix}-translation`}>
            {m.packages.translation}
          </label>
          <input
            id={`${idPrefix}-translation`}
            className="field"
            maxLength={300}
            value={fields.translation}
            onChange={set('translation')}
          />
        </div>
      </div>
      {showHints && (
        <div className="space-y-1 text-sm text-ink-soft">
          <p>
            <strong className="font-semibold">{m.packages.extra}:</strong> {m.packages.extraHint}
          </p>
          <p>
            <strong className="font-semibold">{m.packages.translation}:</strong>{' '}
            {m.packages.translationHint}
          </p>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
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

function VocabRow({ vocab }: { vocab: VocabDto }) {
  const { m } = useI18n();
  const [editing, setEditing] = useState(false);
  const update = useUpdateVocab();
  const remove = useDeleteVocab();
  const activate = useActivateVocab();

  if (editing) {
    return (
      <li className="px-5 py-4">
        <VocabForm
          idPrefix={`edit-${vocab.id}`}
          initial={{ word: vocab.word, extra: vocab.extra, translation: vocab.translation }}
          submitLabel={m.packages.save}
          pending={update.isPending}
          onCancel={() => setEditing(false)}
          onSubmit={(fields) =>
            update.mutate({ id: vocab.id, ...fields }, { onSuccess: () => setEditing(false) })
          }
        />
      </li>
    );
  }

  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3.5">
      <div className="min-w-0 flex-1 basis-56">
        <p className="font-semibold break-words">
          {vocab.word}
          {vocab.extra && <span className="ml-2 font-normal text-ink-soft">{vocab.extra}</span>}
        </p>
        <p className="text-ink-soft break-words">{vocab.translation}</p>
      </div>
      <StageBadge vocab={vocab} />
      <div className="flex items-center gap-1">
        {!vocab.active && (
          <button
            type="button"
            className="btn btn-secondary min-h-9 px-3.5 text-sm"
            onClick={() => activate.mutate(vocab.id)}
            disabled={activate.isPending}
          >
            {m.packages.activate}
          </button>
        )}
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-2.5"
          aria-label={`${m.packages.edit}: ${vocab.word}`}
          onClick={() => setEditing(true)}
        >
          <Pencil className="size-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-2.5"
          aria-label={`${m.packages.delete}: ${vocab.word}`}
          disabled={remove.isPending}
          onClick={() => {
            if (window.confirm(fill(m.packages.deleteVocabConfirm, { word: vocab.word })))
              remove.mutate(vocab.id);
          }}
        >
          <Trash2 className="size-4" aria-hidden="true" />
        </button>
      </div>
    </li>
  );
}

function PackageHeader({ pkg }: { pkg: PackageDto }) {
  const { m } = useI18n();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const update = useUpdatePackage(pkg.id);
  const remove = useDeletePackage(pkg.id);

  if (editing) {
    return (
      <section className="panel mb-6">
        <PackageForm
          initial={pkg}
          submitLabel={m.packages.save}
          pending={update.isPending}
          onCancel={() => setEditing(false)}
          onSubmit={(input) => update.mutate(input, { onSuccess: () => setEditing(false) })}
        />
      </section>
    );
  }

  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <h1 className="text-2xl font-bold tracking-tight break-words sm:text-3xl">{pkg.name}</h1>
        <p className="mt-1 text-ink-soft">
          {languageName(m, pkg.language)} · {m.packages.directions[pkg.direction]} ·{' '}
          {fill(m.packages.words, { n: pkg.counts.total })}
          {pkg.counts.learned > 0 &&
            ` · ${fill(m.packages.learnedCount, { n: pkg.counts.learned })}`}
        </p>
      </div>
      <div className="flex flex-wrap gap-2">
        {pkg.counts.due > 0 && (
          <Link to={`/learn?package=${pkg.id}`} className="btn btn-primary">
            <GraduationCap className="size-4.5" aria-hidden="true" />
            {m.learn.start} ({pkg.counts.due})
          </Link>
        )}
        <button type="button" className="btn btn-secondary" onClick={() => setEditing(true)}>
          <Pencil className="size-4" aria-hidden="true" />
          {m.packages.edit}
        </button>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={remove.isPending}
          onClick={() => {
            if (window.confirm(fill(m.packages.deletePackageConfirm, { name: pkg.name }))) {
              remove.mutate(undefined, { onSuccess: () => void navigate('/packages') });
            }
          }}
        >
          <Trash2 className="size-4" aria-hidden="true" />
          {m.packages.delete}
        </button>
      </div>
    </div>
  );
}

export function PackageDetailPage() {
  const { m } = useI18n();
  const id = Number(useParams().id);
  const query = usePackage(id);
  const add = useAddVocab(id);
  const activateAll = useActivatePackage(id);

  return (
    <>
      <Link to="/packages" className="btn btn-ghost -ml-3 mb-2 px-3">
        <ChevronLeft className="size-4.5" aria-hidden="true" />
        {m.packages.back}
      </Link>

      {query.isPending ? (
        <p role="status" className="text-ink-soft">
          {m.common.loading}
        </p>
      ) : query.isError ? (
        <Notice tone="error">{errorMessage(m, query.error)}</Notice>
      ) : (
        <>
          <PackageHeader pkg={query.data.package} />

          <section className="panel mb-6" aria-labelledby="add-title">
            <h2 id="add-title" className="mb-4 text-lg font-bold">
              {m.packages.addTitle}
            </h2>
            <VocabForm
              idPrefix="add"
              submitLabel={m.packages.add}
              pending={add.isPending}
              showHints
              onSubmit={(fields, reset) => add.mutate([fields], { onSuccess: reset })}
            />
            {add.isError && (
              <div className="mt-3">
                <Notice tone="error">{errorMessage(m, add.error)}</Notice>
              </div>
            )}
          </section>

          <section aria-labelledby="vocab-title">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
              <h2 id="vocab-title" className="text-lg font-bold">
                {m.packages.vocabList}
              </h2>
              {query.data.package.counts.inactive > 0 && (
                <button
                  type="button"
                  className="btn btn-primary"
                  onClick={() => activateAll.mutate()}
                  disabled={activateAll.isPending}
                >
                  {fill(m.packages.activateAll, { n: query.data.package.counts.inactive })}
                </button>
              )}
            </div>
            {query.data.package.counts.inactive > 0 && (
              <p className="mb-3 text-sm text-ink-soft">{m.packages.activateHint}</p>
            )}
            {query.data.vocab.length === 0 ? (
              <p className="rounded-[var(--radius-card)] border border-dashed border-rule-strong px-6 py-10 text-center text-ink-soft">
                {m.packages.noVocab}
              </p>
            ) : (
              <ul className="divide-y divide-rule overflow-hidden rounded-[var(--radius-card)] bg-surface shadow-[var(--shadow-card)]">
                {query.data.vocab.map((v) => (
                  <VocabRow key={v.id} vocab={v} />
                ))}
              </ul>
            )}
          </section>

          <PackagePhotos packageId={id} />
        </>
      )}
    </>
  );
}
