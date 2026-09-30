import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AnswerInput, AnswerResult, LearnCard } from '@wordflow/shared';
import { api } from './api';
import { PACKAGES_KEY } from './packages';

export function useDueCards(packageId: number | null, enabled: boolean) {
  return useQuery({
    queryKey: ['learn', 'cards', packageId],
    queryFn: async () =>
      (
        await api<{ cards: LearnCard[] }>(
          `/learn/cards${packageId ? `?packageId=${packageId}` : ''}`,
        )
      ).cards,
    enabled,
    staleTime: 0,
    gcTime: 0,
  });
}

function useRefreshAfterLearning() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: PACKAGES_KEY });
    void qc.invalidateQueries({ queryKey: ['overview'] });
  };
}

export function useAnswer() {
  const refresh = useRefreshAfterLearning();
  return useMutation({
    mutationFn: (input: AnswerInput) =>
      api<AnswerResult>('/learn/answer', { method: 'POST', body: input }),
    onSuccess: refresh,
  });
}

export function useOverride() {
  const refresh = useRefreshAfterLearning();
  return useMutation({
    mutationFn: (attemptId: number) =>
      api<AnswerResult>(`/learn/attempts/${attemptId}/override`, { method: 'POST' }),
    onSuccess: refresh,
  });
}

/** Loads the checking model in the background (Ollama keep_alive, open connections). */
export function warmUp() {
  void api<void>('/learn/warmup', { method: 'POST' }).catch(() => undefined);
}
