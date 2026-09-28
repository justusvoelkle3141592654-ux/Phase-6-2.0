import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UpdateSettingsInput, UserDto, UserSettings } from '@vokabeltrainer/shared';
import { api } from './api';
import { ME_KEY } from './auth';

export const SETTINGS_KEY = ['settings'] as const;

export function useSettings(enabled = true) {
  return useQuery({
    queryKey: SETTINGS_KEY,
    enabled,
    queryFn: async () => (await api<{ settings: UserSettings }>('/settings')).settings,
  });
}

export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: UpdateSettingsInput) =>
      (await api<{ settings: UserSettings }>('/settings', { method: 'PATCH', body: input }))
        .settings,
    onSuccess: (settings) => qc.setQueryData(SETTINGS_KEY, settings),
  });
}

export function useCompleteSetup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () =>
      (await api<{ user: UserDto }>('/setup/complete', { method: 'POST' })).user,
    onSuccess: (user) => qc.setQueryData(ME_KEY, user),
  });
}
