import { useQuery } from '@tanstack/react-query';
import type { OverviewDto } from '@wordflow/shared';
import { api } from './api';

export function useOverview() {
  return useQuery({ queryKey: ['overview'], queryFn: () => api<OverviewDto>('/overview') });
}
