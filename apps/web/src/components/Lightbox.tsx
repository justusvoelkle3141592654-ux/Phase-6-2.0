import { useEffect, useRef } from 'react';
import { X } from 'lucide-react';
import { useI18n } from '../i18n';
import { AuthImage } from './AuthImage';

/** Full-screen view of a stored photo (native <dialog>: Esc closes it, focus is trapped). */
export function Lightbox({ src, alt, onClose }: { src: string; alt: string; onClose: () => void }) {
  const { m } = useI18n();
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal?.();
  }, []);
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto max-h-[92dvh] max-w-[94vw] overflow-visible bg-transparent p-0 backdrop:bg-black/80"
    >
      <AuthImage
        src={src}
        alt={alt}
        className="max-h-[92dvh] max-w-[94vw] rounded-2xl object-contain"
      />
      <button
        type="button"
        onClick={onClose}
        className="glass absolute top-3 right-3 grid size-10 place-items-center rounded-full text-ink"
        aria-label={m.upload.close}
        autoFocus
      >
        <X className="size-5" aria-hidden="true" />
      </button>
    </dialog>
  );
}
