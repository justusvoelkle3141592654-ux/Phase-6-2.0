import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  AuthResponse,
  ChangePasswordInput,
  LoginInput,
  RegisterInput,
  UpdateProfileInput,
  UserDto,
} from '@gero/shared';
import { api, ApiError } from './api';

export const ME_KEY = ['me'] as const;

/** Current user, or null when signed out. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<UserDto | null> => {
      try {
        return (await api<{ user: UserDto }>('/auth/me')).user;
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) return null;
        throw err;
      }
    },
    staleTime: Infinity,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: LoginInput) =>
      api<AuthResponse>('/auth/login', { method: 'POST', body: input }),
    onSuccess: ({ user }) => qc.setQueryData(ME_KEY, user),
  });
}

export function useRegister() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: RegisterInput) =>
      api<AuthResponse>('/auth/register', { method: 'POST', body: input }),
    onSuccess: ({ user }) => qc.setQueryData(ME_KEY, user),
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>('/auth/logout', { method: 'POST' }),
    onSuccess: () => {
      // Keep the `me` query (the UI depends on it) and set it to "signed out";
      // everything else belonged to the previous account and is dropped.
      qc.removeQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] });
      qc.setQueryData(ME_KEY, null);
    },
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateProfileInput) =>
      api<{ user: UserDto }>('/auth/me', { method: 'PATCH', body: input }),
    onSuccess: ({ user }) => qc.setQueryData(ME_KEY, user),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (input: ChangePasswordInput) =>
      api<void>('/auth/password', { method: 'POST', body: input }),
  });
}
