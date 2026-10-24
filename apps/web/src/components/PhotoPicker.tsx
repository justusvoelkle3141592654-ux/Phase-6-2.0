import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Camera, Images, Upload, X } from 'lucide-react';
import { useI18n } from '../i18n';
import { errorMessage } from '../lib/errors';
import { fill } from '../lib/format';
import type { PhotoUpload } from '../lib/uploads';
import { Notice } from './Notice';

const MAX_FILES = 20;

interface Selected {
  file: File;
  url: string;
}

/**
 * Pick photos with the camera or from the gallery, preview them and send them.
 * Used for "Hochladen" (attach only) and "Erkennen" (AI recognition).
 */
export function PhotoPicker({
  title,
  intro,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
}: {
  title: string;
  intro: ReactNode;
  submitLabel: string;
  pending: boolean;
  error: unknown;
  onSubmit: (input: PhotoUpload, done: () => void) => void;
  onCancel: () => void;
}) {
  const { m } = useI18n();
  const cameraRef = useRef<HTMLInputElement>(null);
  const galleryRef = useRef<HTMLInputElement>(null);
  const [selected, setSelected] = useState<Selected[]>([]);
  const [prepared, setPrepared] = useState(0);

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
    onSubmit({ files: selected.map((s) => s.file), onProgress: setPrepared }, () =>
      setSelected([]),
    );
  };

  return (
    <section className="panel mb-6" aria-label={title}>
      <div className="mb-4 flex items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold">{title}</h2>
          <p className="mt-1 text-ink-soft">{intro}</p>
        </div>
        <button
          type="button"
          className="btn btn-ghost min-h-9 px-2.5"
          aria-label={m.packages.cancel}
          onClick={onCancel}
          disabled={pending}
        >
          <X className="size-4.5" aria-hidden="true" />
        </button>
      </div>
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
                  disabled={pending}
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
            disabled={pending}
          >
            <Upload className="size-5" aria-hidden="true" />
            {pending
              ? prepared < selected.length
                ? fill(m.upload.preparing, { done: prepared, total: selected.length })
                : m.upload.uploading
              : submitLabel}
          </button>
        </div>
      )}
      {error != null && (
        <div className="mt-4">
          <Notice tone="error">{errorMessage(m, error)}</Notice>
        </div>
      )}
    </section>
  );
}
