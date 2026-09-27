import { useState } from 'react';
import { useI18n } from '../i18n';
import { fill } from '../lib/format';
import { usePackagePhotos } from '../lib/uploads';
import { AuthImage } from './AuthImage';
import { Lightbox } from './Lightbox';

/** The notebook photos a package was recognised from. */
export function PackagePhotos({ packageId }: { packageId: number }) {
  const { m } = useI18n();
  const photos = usePackagePhotos(packageId);
  const [open, setOpen] = useState<string | null>(null);
  if (!photos.data || photos.data.length === 0) return null;
  return (
    <section className="mt-8" aria-labelledby="photos-title">
      <h2 id="photos-title" className="mb-3 text-lg font-bold">
        {m.upload.photos}
      </h2>
      <ul className="grid grid-cols-3 gap-2 sm:grid-cols-6">
        {photos.data.map((p, i) => (
          <li key={p.id}>
            <button
              type="button"
              className="block w-full overflow-hidden rounded-xl"
              onClick={() => setOpen(p.photoUrl)}
              aria-label={`${m.upload.zoom}: ${fill(m.upload.photo, { n: i + 1 })}`}
            >
              <AuthImage
                src={p.photoUrl}
                alt={m.upload.photoAlt}
                className="aspect-[3/4] w-full object-cover"
              />
            </button>
          </li>
        ))}
      </ul>
      {open && <Lightbox src={open} alt={m.upload.photoAlt} onClose={() => setOpen(null)} />}
    </section>
  );
}
