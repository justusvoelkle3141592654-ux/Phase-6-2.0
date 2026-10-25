import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  AdminCreateUserInput,
  AdminUpdateUserInput,
  AdminUserDto,
  UpdateSourceInput,
  UpdateStatusDto,
} from '@wordflow/shared';
import { api, apiFetch, ApiError } from './api';
import { ME_KEY } from './auth';
import { setToken } from './platform';

const USERS_KEY = ['admin', 'users'] as const;
const UPDATE_KEY = ['admin', 'update'] as const;

export function useAdminUsers() {
  return useQuery({
    queryKey: USERS_KEY,
    queryFn: async () => (await api<{ users: AdminUserDto[] }>('/admin/users')).users,
  });
}

export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: AdminCreateUserInput) =>
      api<{ user: AdminUserDto }>('/admin/users', { method: 'POST', body: input }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: USERS_KEY }),
  });
}

export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: AdminUpdateUserInput & { id: number }) =>
      api<{ user: AdminUserDto }>(`/admin/users/${id}`, { method: 'PATCH', body: input }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: USERS_KEY });
      void qc.invalidateQueries({ queryKey: ME_KEY });
    },
  });
}

export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/admin/users/${id}`, { method: 'DELETE' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: USERS_KEY }),
  });
}

/** Downloads the backup ZIP (works with cookie and with the app's bearer token). */
export function useDownloadBackup() {
  return useMutation({
    mutationFn: async () => {
      const res = await apiFetch('/api/admin/backup');
      if (!res.ok) {
        const data = await res.json().catch(() => null);
        throw new ApiError(res.status, data?.error ?? 'internal_error');
      }
      const name =
        /filename="([^"]+)"/.exec(res.headers.get('Content-Disposition') ?? '')?.[1] ??
        'wordflow-backup.zip';
      const url = URL.createObjectURL(await res.blob());
      const link = document.createElement('a');
      link.href = url;
      link.download = name;
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    },
  });
}

/** Replaces all data with a backup ZIP; signs out afterwards. */
export function useRestoreBackup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData();
      form.append('backup', file, file.name);
      await api<void>('/admin/restore', { method: 'POST', form });
      await setToken(null);
    },
    onSuccess: () => {
      qc.clear();
      qc.setQueryData(ME_KEY, null);
    },
  });
}

/** Installed vs. newest version on GitHub; polls while an update runs. */
export function useUpdateStatus() {
  return useQuery({
    queryKey: UPDATE_KEY,
    queryFn: () => api<UpdateStatusDto>('/admin/update'),
    refetchInterval: (q) => (q.state.data?.running ? 2000 : false),
  });
}

/** Saves repository, branch and optionally the GitHub token of the update. */
export function useSaveUpdateSource() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateSourceInput) =>
      api<{ repo: string; branch: string; hasToken: boolean }>('/admin/update/source', {
        method: 'PUT',
        body: input,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: UPDATE_KEY }),
  });
}

export function useStartUpdate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<{ started: boolean }>('/admin/update', { method: 'POST' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: UPDATE_KEY }),
  });
}
