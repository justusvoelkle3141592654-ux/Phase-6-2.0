import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { PackagePhotoDto, SaveUploadInput, UploadJobDto } from '@gero/shared';
import { api } from './api';
import { PACKAGES_KEY } from './packages';
import { resizePhoto } from './resize';

const busy = (job?: UploadJobDto) =>
  job?.pages.some((p) => p.status === 'pending' || p.status === 'processing');

export function useOpenUploads() {
  return useQuery({
    queryKey: ['uploads'],
    queryFn: async () => (await api<{ jobs: UploadJobDto[] }>('/uploads')).jobs,
    refetchInterval: (q) => (q.state.data?.some((j) => busy(j)) ? 2000 : false),
  });
}

/** One upload; polls while photos are still being recognised. */
export function useUploadJob(id: number) {
  return useQuery({
    queryKey: ['uploads', id],
    queryFn: async () => (await api<{ job: UploadJobDto }>(`/uploads/${id}`)).job,
    refetchInterval: (q) => (busy(q.state.data) ? 1500 : false),
  });
}

export function useUploadPhotos() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      files,
      onProgress,
    }: {
      files: File[];
      onProgress?: (done: number) => void;
    }) => {
      const form = new FormData();
      let done = 0;
      for (const file of files) {
        const blob = await resizePhoto(file);
        const name = file.name.replace(/\.[^.]+$/, '') + (blob.type === 'image/jpeg' ? '.jpg' : '');
        form.append('photos', blob, name || 'photo.jpg');
        onProgress?.(++done);
      }
      return (await api<{ job: UploadJobDto }>('/uploads', { method: 'POST', form })).job;
    },
    onSuccess: (job) => {
      qc.setQueryData(['uploads', job.id], job);
      void qc.invalidateQueries({ queryKey: ['uploads'], exact: true });
    },
  });
}

export function useRetryPage(jobId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (pageId: number) =>
      (await api<{ job: UploadJobDto }>(`/uploads/pages/${pageId}/retry`, { method: 'POST' })).job,
    onSuccess: (job) => qc.setQueryData(['uploads', jobId], job),
  });
}

export function useSaveUpload(jobId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: SaveUploadInput) =>
      api<{ packageId: number; count: number }>(`/uploads/${jobId}/save`, {
        method: 'POST',
        body: input,
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['uploads'] });
      void qc.invalidateQueries({ queryKey: PACKAGES_KEY });
      void qc.invalidateQueries({ queryKey: ['overview'] });
    },
  });
}

export function useDeleteUpload(jobId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>(`/uploads/${jobId}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.removeQueries({ queryKey: ['uploads', jobId] });
      void qc.invalidateQueries({ queryKey: ['uploads'], exact: true });
    },
  });
}

export function usePackagePhotos(packageId: number) {
  return useQuery({
    queryKey: ['packages', packageId, 'photos'],
    queryFn: async () =>
      (await api<{ photos: PackagePhotoDto[] }>(`/packages/${packageId}/photos`)).photos,
  });
}
