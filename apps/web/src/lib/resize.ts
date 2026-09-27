/** Longest edge of uploaded photos; enough to read handwriting, small enough to upload fast. */
export const MAX_EDGE = 2000;

/**
 * Scales a photo down to at most 2000 px on the long edge and re-encodes it
 * as JPEG. EXIF rotation is applied. Falls back to the original file when the
 * browser cannot decode it.
 */
export async function resizePhoto(file: File, maxEdge = MAX_EDGE, quality = 0.85): Promise<Blob> {
  try {
    const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement('canvas');
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bitmap, 0, 0, width, height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', quality),
    );
    return blob ?? file;
  } catch {
    return file;
  }
}
