import { createContext, useContext, useEffect, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ensureCsrf } from '../../core/http/apiClient';
import type { Me } from '../../core/domain/types';

type AuthState = { user: Me | null; loading: boolean; login: () => void; logout: () => Promise<void>; can: (permission: string) => boolean };
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['auth', 'me'], queryFn: async () => (await api.get<Me>('/auth/me')).data, retry: false, staleTime: 30_000 });
  const user = query.data || null;
  useEffect(() => {
    const expired = () => { queryClient.clear(); window.location.assign('/login?reason=session-expired'); };
    window.addEventListener('erp:session-expired', expired);
    return () => window.removeEventListener('erp:session-expired', expired);
  }, [queryClient]);
  async function logout() {
    const csrf = await ensureCsrf();
    queryClient.clear();
    const form = document.createElement('form');
    form.method = 'POST'; form.action = '/api/v1/auth/logout';
    const token = document.createElement('input');
    token.type = 'hidden'; token.name = '_csrf'; token.value = csrf;
    form.append(token); document.body.append(form); form.submit();
  }
  const can = (permission: string) => !!user && (user.permissions.includes(permission) || user.permissions.includes('super-admin') || user.permissions.includes('ROLE_SUPER_ADMIN'));
  return <AuthContext.Provider value={{ user, loading: query.isLoading, login: () => window.location.assign('/oauth2/authorization/keycloak'), logout, can }}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
