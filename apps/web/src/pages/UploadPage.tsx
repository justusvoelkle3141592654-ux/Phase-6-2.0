import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { Camera, ChevronRight, Images, Upload, X } from 'lucide-react';
import { Notice } from '../components/Notice';
import { PageHeader } from '../components/Page';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import { useOpenUploads, useUploadPhotos } from '../lib/uploads';

const MAX_FILES = 20;

interface Selected {
  file: File;
  url: string;
}

export function UploadPage() {
  const { m, lang } = useI18n();
  const navigate = useNavigate();
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [selected, setSelected] = useState<Selected[]>([]);
  const [prepared, setPrepared] = useState(0);
  const upload = useUploadPhotos();
  const open = useOpenUploads();

  // Free the preview URLs.
  useEffect(() => () => selected.forEach((s) => URL.revokeObjectURL(s.url)), [selected]);

  const add = (files: FileList | null) => {
    if (!files) return;
    const next = [...files]
      .filter((f) => f.type.startsWith('image/'))
      .map((file) => ({ file, url: URL.createObjectURL(file) }));
    setSelected((s) => [...s, ...next].slice(0, MAX_FILES));
  };

  const start = () => {
    setPrepared(0);
    upload.mutate(
      { files: selected.map((s) => s.file), onProgress: setPrepared },
      {
        onSuccess: (job) => {
          setSelected([]);
          void navigate(`/upload/${job.id}`);
        },
      },
    );
  };

  const dateFormat = new Intl.DateTimeFormat(lang === 'de' ? 'de-DE' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });

  return (
    <>
      <PageHeader title={m.upload.title} />
      <div className="grid max-w-3xl grid-cols-[minmax(0,1fr)] gap-6">
        <section className="panel">
          <p className="mb-4 text-ink-soft">{m.upload.empty}</p>
          {/* Two buttons: an Android WebView opens a plain file input straight in the file browser. */}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => cameraRef.current?.click()}
            >
              <Camera className="size-5" aria-hidden="true" />
              {m.upload.camera}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => galleryRef.current?.click()}
            >
              <Images className="size-5" aria-hidden="true" />
              {m.upload.gallery}
            </button>
          </div>
          <input
            ref={cameraRef}
            type="file"
            accept="image/*"
            capture="environment"
            className="hidden"
            data-testid="camera-input"
            onChange={(e) => {
              add(e.target.files);
              e.target.value = '';
            }}
          />
          <input
            ref={galleryRef}
            type="file"
            accept="image/*"
            multiple
            className="hidden"
            data-testid="gallery-input"
            onChange={(e) => {
              add(e.target.files);
              e.target.value = '';
            }}
          />

          {selected.length > 0 && (
            <div className="mt-5 space-y-4">
              <p className="text-sm font-semibold text-ink-soft">
                {fill(m.upload.selected, { n: selected.length })}
              </p>
              <ul className="grid grid-cols-3 gap-2 sm:grid-cols-5">
                {selected.map((s, i) => (
                  <li key={s.url} className="relative">
                    <img
                      src={s.url}
                      alt={fill(m.upload.photo, { n: i + 1 })}
                      className="aspect-[3/4] w-full rounded-xl object-cover"
                    />
                    <button
                      type="button"
                      className="glass absolute top-1.5 right-1.5 grid size-8 place-items-center rounded-full"
                      aria-label={`${m.upload.remove} ${i + 1}`}
                      onClick={() => setSelected((all) => all.filter((x) => x !== s))}
                      disabled={upload.isPending}
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  </li>
                ))}
              </ul>
              <button
                type="button"
                className="btn btn-primary w-full sm:w-auto"
                onClick={start}
                disabled={upload.isPending}
              >
                <Upload className="size-5" aria-hidden="true" />
                {upload.isPending
                  ? prepared < selected.length
                    ? fill(m.upload.preparing, { done: prepared, total: selected.length })
                    : m.upload.uploading
                  : m.upload.start}
              </button>
            </div>
          )}
          {upload.isError && (
            <div className="mt-4">
              <Notice tone="error">{errorMessage(m, upload.error)}</Notice>
            </div>
          )}
        </section>

        {open.data && open.data.length > 0 && (
          <section aria-labelledby="open-title">
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
        )}
      </div>
    </>
  );
}
