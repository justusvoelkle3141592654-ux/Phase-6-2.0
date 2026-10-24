import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type {
  AuthResponse,
  ChangePasswordInput,
  DeleteAccountInput,
  LoginInput,
  UpdateProfileInput,
  UserDto,
} from '@wordflow/shared';
import { api, ApiError } from './api';
import { getToken, isApp, setToken } from './platform';

const CLIENT = isApp ? 'app' : 'web';

export const ME_KEY = ['me'] as const;

/** True when the superuser is signed in. */
export function useIsAdmin(): boolean {
  return useMe().data?.role === 'admin';
}

/** Current user, or null when signed out. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: async (): Promise<UserDto | null> => {
      if (isApp && !getToken()) return null;
      try {
        return (await api<{ user: UserDto }>('/auth/me')).user;
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          await setToken(null);
          return null;
        }
        throw err;
      }
    },
    staleTime: Infinity,
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: LoginInput) => {
      const res = await api<AuthResponse>('/auth/login', {
        method: 'POST',
        body: { ...input, client: CLIENT },
      });
      if (res.token) await setToken(res.token);
      return res;
    },
    onSuccess: ({ user }) => qc.setQueryData(ME_KEY, user),
  });
}

/** Keeps the `me` query (the UI depends on it) and sets it to "signed out";
 * everything else belonged to the previous account and is dropped. */
function signedOut(qc: ReturnType<typeof useQueryClient>) {
  qc.removeQueries({ predicate: (q) => q.queryKey[0] !== ME_KEY[0] });
  qc.setQueryData(ME_KEY, null);
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api<void>('/auth/logout', { method: 'POST' });
      await setToken(null);
    },
    onSuccess: () => signedOut(qc),
  });
}

export function useDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: DeleteAccountInput) => {
      await api<void>('/auth/me', { method: 'DELETE', body: input });
      await setToken(null);
    },
    onSuccess: () => signedOut(qc),
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
