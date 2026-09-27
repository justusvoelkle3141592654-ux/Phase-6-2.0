import { z } from 'zod';
import { DIRECTIONS } from './packages';

/** One recognised line of the notebook. */
export const recognizedEntrySchema = z.object({
  word: z.string().trim().min(1).max(200),
  extra: z.string().trim().max(300).optional().default(''),
  translations: z
    .union([z.array(z.string()), z.string()])
    .transform((t) => (Array.isArray(t) ? t : [t]).map((s) => s.trim()).filter(Boolean))
    .pipe(z.array(z.string().max(300)).min(1)),
  language: z.string().trim().max(40).optional(),
});
export const recognitionSchema = z.object({ entries: z.array(recognizedEntrySchema).max(300) });
export type RecognizedEntry = z.output<typeof recognizedEntrySchema>;

export const PAGE_STATUSES = ['pending', 'processing', 'done', 'failed'] as const;
export type PageStatus = (typeof PAGE_STATUSES)[number];

export interface UploadPageDto {
  id: number;
  status: PageStatus;
  /** Error code, e.g. "no_vision_model", "invalid_format", "interrupted", or a provider error code. */
  error: string | null;
  errorMessage: string | null;
  entries: RecognizedEntry[];
  /** API path of the stored photo. */
  photoUrl: string;
}

export interface UploadJobDto {
  id: number;
  createdAt: string;
  /** Set once the words were saved into a package. */
  packageId: number | null;
  pages: UploadPageDto[];
}

export const saveUploadSchema = z.object({
  target: z.union([
    z.object({ packageId: z.int().positive() }),
    z.object({
      newPackage: z.object({
        name: z.string().trim().min(1).max(100),
        language: z.string().trim().min(1).max(40),
        direction: z.enum(DIRECTIONS).default('foreign_native'),
      }),
    }),
  ]),
  items: z
    .array(
      z.object({
        word: z.string().trim().min(1).max(200),
        extra: z.string().trim().max(300).default(''),
        translation: z.string().trim().min(1).max(300),
      }),
    )
    .min(1)
    .max(1000),
});
export type SaveUploadInput = z.input<typeof saveUploadSchema>;

export interface PackagePhotoDto {
  id: number;
  photoUrl: string;
  createdAt: string;
}
