import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  AiConfigDto,
  AiTask,
  LatencyStatsDto,
  ModelInfoDto,
  ProviderCreateInput,
  ProviderDto,
  ProviderUpdateInput,
  TaskModelInput,
  TestResultDto,
} from '@wordflow/shared';
import { api } from './api';

export const AI_CONFIG_KEY = ['ai', 'config'] as const;

export function useAiConfig() {
  return useQuery({ queryKey: AI_CONFIG_KEY, queryFn: () => api<AiConfigDto>('/ai/config') });
}

export function useModels(providerId: number | null, vision: boolean) {
  return useQuery({
    queryKey: ['ai', 'models', providerId, vision],
    queryFn: async () =>
      (
        await api<{ models: ModelInfoDto[] }>(
          `/ai/providers/${providerId}/models${vision ? '?vision=1' : ''}`,
        )
      ).models,
    enabled: providerId !== null,
    retry: false,
    staleTime: 5 * 60_000,
  });
}

function useRefreshConfig() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ['ai'] });
  };
}

export function useCreateProvider() {
  const refresh = useRefreshConfig();
  return useMutation({
    mutationFn: async (input: ProviderCreateInput) =>
      (await api<{ provider: ProviderDto }>('/ai/providers', { method: 'POST', body: input }))
        .provider,
    onSuccess: refresh,
  });
}

export function useUpdateProvider() {
  const refresh = useRefreshConfig();
  return useMutation({
    mutationFn: ({ id, ...input }: ProviderUpdateInput & { id: number }) =>
      api<{ provider: ProviderDto }>(`/ai/providers/${id}`, { method: 'PATCH', body: input }),
    onSuccess: refresh,
  });
}

export function useDeleteProvider() {
  const refresh = useRefreshConfig();
  return useMutation({
    mutationFn: (id: number) => api<void>(`/ai/providers/${id}`, { method: 'DELETE' }),
    onSuccess: refresh,
  });
}

export function useTestConnection() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ providerId, model }: { providerId: number; model?: string }) =>
      api<TestResultDto>(`/ai/providers/${providerId}/test`, { method: 'POST', body: { model } }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['ai', 'latency'] }),
  });
}

export function useSetTaskModel() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ task, ...input }: TaskModelInput & { task: AiTask }) =>
      api<AiConfigDto>(`/ai/tasks/${task}`, { method: 'PUT', body: input }),
    onSuccess: (config) => qc.setQueryData(AI_CONFIG_KEY, config),
  });
}

export function useLatency() {
  return useQuery({
    queryKey: ['ai', 'latency'],
    queryFn: async () => (await api<{ stats: LatencyStatsDto[] }>('/ai/latency')).stats,
  });
}
