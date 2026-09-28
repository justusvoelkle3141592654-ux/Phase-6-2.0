import { useQuery } from '@tanstack/react-query';
import type { OverviewDto, SummaryDto } from '@vokabeltrainer/shared';
import { api } from './api';

export function useOverview() {
  return useQuery({ queryKey: ['overview'], queryFn: () => api<OverviewDto>('/overview') });
}

/** Only regenerated on the server when new answers came in; keyed by the answer count. */
export function useSummary(attempts: number | undefined) {
  return useQuery({
    queryKey: ['overview', 'summary', attempts],
    queryFn: () => api<SummaryDto>('/overview/summary'),
    enabled: attempts !== undefined && attempts > 0,
    staleTime: Infinity,
    retry: false,
  });
}
