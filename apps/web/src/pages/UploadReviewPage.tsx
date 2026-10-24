import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { ChevronLeft, Maximize2, Plus, RotateCcw, Trash2 } from 'lucide-react';
import type { RecognizedEntry, UploadPageDto } from '@wordflow/shared';
import { AuthImage } from '../components/AuthImage';
import { Lightbox } from '../components/Lightbox';
import { Notice } from '../components/Notice';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { usePackages } from '../lib/packages';
import { useDeleteUpload, useRetryPage, useSaveUpload, useUploadJob } from '../lib/uploads';

interface Row {
  key: number;
  word: string;
  extra: string;
  translation: string;
}

let nextKey = 1;
const toRow = (e: RecognizedEntry): Row => ({
  key: nextKey++,
  word: e.word,
  extra: e.extra,
  translation: e.translations.join('; '),
});
const emptyRow = (): Row => ({ key: nextKey++, word: '', extra: '', translation: '' });
const complete = (r: Row) => r.word.trim() !== '' && r.translation.trim() !== '';

function PageEditor({
  page,
  index,
  rows,
  setRows,
  jobId,
}: {
  page: UploadPageDto;
  index: number;
  rows: Row[] | undefined;
  setRows: (rows: Row[]) => void;
  jobId: number;
}) {
  const { m } = useI18n();
  const retry = useRetryPage(jobId);
  const [zoom, setZoom] = useState(false);
  const label = fill(m.upload.photo, { n: index + 1 });
  const update = (key: number, field: keyof Omit<Row, 'key'>, value: string) =>
    setRows((rows ?? []).map((r) => (r.key === key ? { ...r, [field]: value } : r)));

  return (
    <section
      className="panel grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]"
      aria-label={label}
    >
      <div>
        <button
          type="button"
          className="relative block w-full overflow-hidden rounded-2xl"
          onClick={() => setZoom(true)}
          aria-label={`${m.upload.zoom}: ${label}`}
        >
          <AuthImage
            src={page.photoUrl}
            alt={label}
            className="max-h-[28rem] w-full bg-surface-2 object-contain lg:max-h-[36rem]"
          />
          <span className="glass absolute right-2 bottom-2 grid size-9 place-items-center rounded-full">
            <Maximize2 className="size-4" aria-hidden="true" />
          </span>
        </button>
        {zoom && <Lightbox src={page.photoUrl} alt={label} onClose={() => setZoom(false)} />}
      </div>

      <div className="min-w-0">
        <h2 className="mb-3 font-bold">{label}</h2>
        {(page.status === 'pending' || page.status === 'processing') && (
          <p role="status" className="animate-pulse text-ink-soft">
            {page.status === 'pending' ? m.upload.pending : m.upload.processing}
          </p>
        )}
        {page.status === 'failed' && (
          <div className="space-y-3">
            <Notice tone="error">
              {`${m.upload.failed}: ${
                (m.upload.errors as Record<string, string>)[page.error ?? ''] ??
                (m.ai.errors as Record<string, string>)[page.error ?? ''] ??
                page.errorMessage ??
                ''
              }`}
            </Notice>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => retry.mutate(page.id)}
              disabled={retry.isPending}
            >
              <RotateCcw className="size-4" aria-hidden="true" />
              {m.upload.retry}
            </button>
          </div>
        )}
        {page.status === 'done' && rows && (
          <div className="space-y-2">
            {rows.length === 0 && <p className="text-ink-soft">{m.upload.noEntries}</p>}
            {rows.map((r, i) => (
              <div
                key={r.key}
                className="grid grid-cols-[minmax(0,1fr)_auto] gap-2 rounded-2xl bg-surface-2 p-2 sm:grid-cols-[1fr_1fr_1.3fr_auto]"
              >
                <input
                  className="field bg-surface"
                  aria-label={`${m.upload.word} ${i + 1}`}
                  placeholder={m.upload.word}
                  value={r.word}
                  onChange={(e) => update(r.key, 'word', e.target.value)}
                />
                <button
                  type="button"
                  className="btn btn-ghost min-h-11 px-3 sm:order-last"
                  aria-label={`${m.upload.deleteRow} ${i + 1}`}
                  onClick={() => setRows(rows.filter((x) => x.key !== r.key))}
                >
                  <Trash2 className="size-4" aria-hidden="true" />
                </button>
                <input
                  className="field col-span-2 bg-surface sm:col-span-1"
                  aria-label={`${m.upload.extra} ${i + 1}`}
                  placeholder={m.upload.extra}
                  value={r.extra}
                  onChange={(e) => update(r.key, 'extra', e.target.value)}
                />
                <input
                  className="field col-span-2 bg-surface sm:col-span-1"
                  aria-label={`${m.upload.translation} ${i + 1}`}
                  placeholder={m.upload.translation}
                  value={r.translation}
                  onChange={(e) => update(r.key, 'translation', e.target.value)}
                />
              </div>
            ))}
            <button
              type="button"
              className="btn btn-ghost"
              onClick={() => setRows([...rows, emptyRow()])}
            >
              <Plus className="size-4" aria-hidden="true" />
              {m.upload.addRow}
            </button>
          </div>
        )}
      </div>
    </section>
  );
}

export function UploadReviewPage() {
  const { m } = useI18n();
  const navigate = useNavigate();
  const id = Number(useParams().id);
  const [search] = useSearchParams();
  const fromPackage = Number(search.get('package')) || null;
  const job = useUploadJob(id);
  const packages = usePackages();
  const save = useSaveUpload(id);
  const discard = useDeleteUpload(id);
  const [rows, setRows] = useState<Record<number, Row[]>>({});
  const [packageId, setPackageId] = useState<number | null>(fromPackage);
  const backTo = fromPackage ? `/packages/${fromPackage}` : '/packages';

  // Take over the recognition result of each page once it is done (keeps edits).
  const pages = job.data?.pages;
  useEffect(() => {
    if (!pages) return;
    setRows((current) => {
      let changed = false;
      const next = { ...current };
      for (const p of pages) {
        if (p.status === 'done' && !next[p.id]) {
          next[p.id] = p.entries.map(toRow);
          changed = true;
        }
      }
      return changed ? next : current;
    });
  }, [pages]);

  if (job.isPending) return <p className="text-ink-soft">{m.common.loading}</p>;
  if (job.isError) return <Notice tone="error">{errorMessage(m, job.error)}</Notice>;

  const items = Object.values(rows)
    .flat()
    .filter(complete)
    .map((r) => ({
      word: r.word.trim(),
      extra: r.extra.trim(),
      translation: r.translation.trim(),
    }));
  const running = job.data.pages.some((p) => p.status === 'pending' || p.status === 'processing');
  const existing = packages.data ?? [];
  const chosenPackage = packageId ?? existing[0]?.id ?? null;
  const canSave = items.length > 0 && chosenPackage !== null && job.data.packageId === null;

  const onSave = () => {
    save.mutate(
      { packageId: chosenPackage!, items },
      { onSuccess: (res) => void navigate(`/packages/${res.packageId}`) },
    );
  };

  return (
    <>
      <Link to={backTo} className="btn btn-ghost -ml-3 mb-2 px-3">
        <ChevronLeft className="size-4.5" aria-hidden="true" />
        {m.upload.back}
      </Link>
      <PageHeader title={m.upload.reviewTitle} />
      <p className="-mt-3 mb-6 text-ink-soft">{m.upload.reviewHint}</p>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-6">
        {job.data.pages.map((page, i) => (
          <PageEditor
            key={page.id}
            page={page}
            index={i}
            rows={rows[page.id]}
            setRows={(r) => setRows((all) => ({ ...all, [page.id]: r }))}
            jobId={id}
          />
        ))}

        <section className="panel max-w-2xl space-y-4" aria-labelledby="save-title">
          <h2 id="save-title" className="text-lg font-bold">
            {m.upload.saveTitle}
          </h2>
          <div>
            <label className="label" htmlFor="save-package">
              {m.upload.choosePackage}
            </label>
            <select
              id="save-package"
              className="field"
              value={chosenPackage ?? ''}
              onChange={(e) => setPackageId(Number(e.target.value))}
            >
              {existing.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>

          {running && <p className="text-sm text-ink-soft">{m.upload.stillRunning}</p>}
          <p className="text-sm text-ink-soft">{m.upload.saveHint}</p>
          {save.isError && <Notice tone="error">{errorMessage(m, save.error)}</Notice>}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="btn btn-primary"
              disabled={!canSave || save.isPending}
              onClick={onSave}
            >
              {fill(m.upload.save, { n: items.length })}
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={discard.isPending}
              onClick={() => {
                if (window.confirm(m.upload.discardConfirm)) {
                  discard.mutate(undefined, { onSuccess: () => void navigate(backTo) });
                }
              }}
            >
              {m.upload.discard}
            </button>
          </div>
        </section>
      </div>
    </>
  );
}
