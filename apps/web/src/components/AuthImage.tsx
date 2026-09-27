import { useEffect, useState, type ImgHTMLAttributes } from 'react';
import { apiFetch } from '../lib/api';

/**
 * Image from the API. Loaded with the app's credentials (cookie in the
 * browser, token in the Android app) and shown via an object URL.
 */
export function AuthImage({
  src,
  alt,
  ...rest
}: { src: string; alt: string } & ImgHTMLAttributes<HTMLImageElement>) {
  const [url, setUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;
    setFailed(false);
    apiFetch(src)
      .then(async (res) => {
        if (!res.ok) throw new Error(String(res.status));
        const blob = await res.blob();
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => !cancelled && setFailed(true));
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [src]);

  if (!url) {
    return (
      <div
        role="img"
        aria-label={alt}
        className={`${rest.className ?? ''} animate-pulse bg-surface-2 ${failed ? 'animate-none' : ''}`}
      />
    );
  }
  return <img src={url} alt={alt} {...rest} />;
}
