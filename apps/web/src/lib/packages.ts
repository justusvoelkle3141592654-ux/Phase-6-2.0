import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { PackageDto, PackageInput, VocabDto, VocabInput } from '@wordflow/shared';
import { api } from './api';

export const PACKAGES_KEY = ['packages'] as const;
export const packageKey = (id: number) => ['packages', id] as const;

export function usePackages() {
  return useQuery({
    queryKey: PACKAGES_KEY,
    queryFn: async () => (await api<{ packages: PackageDto[] }>('/packages')).packages,
  });
}

export function usePackage(id: number) {
  return useQuery({
    queryKey: packageKey(id),
    queryFn: () => api<{ package: PackageDto; vocab: VocabDto[] }>(`/packages/${id}`),
  });
}

/** Everything that changes words or packages refreshes all package data (and the home page). */
function useInvalidate() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: PACKAGES_KEY });
    void qc.invalidateQueries({ queryKey: ['overview'] });
  };
}

export function useCreatePackage() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: async (input: PackageInput) =>
      (await api<{ package: PackageDto }>('/packages', { method: 'POST', body: input })).package,
    onSuccess: invalidate,
  });
}

export function useUpdatePackage(id: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (input: Partial<PackageInput>) =>
      api<{ package: PackageDto }>(`/packages/${id}`, { method: 'PATCH', body: input }),
    onSuccess: invalidate,
  });
}

export function useDeletePackage(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>(`/packages/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      qc.removeQueries({ queryKey: packageKey(id) });
      void qc.invalidateQueries({ queryKey: PACKAGES_KEY });
      void qc.invalidateQueries({ queryKey: ['overview'] });
    },
  });
}

export function useAddVocab(packageId: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (items: VocabInput[]) =>
      api<{ vocab: VocabDto[] }>(`/packages/${packageId}/vocab`, {
        method: 'POST',
        body: { items },
      }),
    onSuccess: invalidate,
  });
}

export function useUpdateVocab() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: ({ id, ...input }: Partial<VocabInput> & { id: number }) =>
      api<{ vocab: VocabDto }>(`/vocab/${id}`, { method: 'PATCH', body: input }),
    onSuccess: invalidate,
  });
}

export function useDeleteVocab() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/vocab/${id}`, { method: 'DELETE' }),
    onSuccess: invalidate,
  });
}

export function useActivateVocab() {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: (id: number) =>
      api<{ vocab: VocabDto }>(`/vocab/${id}/activate`, { method: 'POST' }),
    onSuccess: invalidate,
  });
}

export function useActivatePackage(id: number) {
  const invalidate = useInvalidate();
  return useMutation({
    mutationFn: () => api<{ activated: number }>(`/packages/${id}/activate`, { method: 'POST' }),
    onSuccess: invalidate,
  });
}
