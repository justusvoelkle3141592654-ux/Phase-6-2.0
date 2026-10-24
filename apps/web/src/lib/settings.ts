import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UpdateSettingsInput, UserSettings } from '@wordflow/shared';
import { api } from './api';

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
    onSuccess: (settings) => {
      qc.setQueryData(SETTINGS_KEY, settings);
      // The daily limit changes what can be learned today.
      void qc.invalidateQueries({ queryKey: ['overview'] });
    },
  });
}
