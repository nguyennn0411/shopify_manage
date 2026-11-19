import { useQuery } from '@tanstack/react-query';
import { api } from '../../core/http/apiClient';

export type CrmRow = Record<string, unknown>;

export function useCrmRows(companyId: string, key: string, endpoint: string, params: Record<string, unknown> = {}, enabled = true) {
  return useQuery({ queryKey: ['crm', key, companyId, params], enabled: !!companyId && enabled,
    queryFn: async () => (await api.get<CrmRow[]>(endpoint, { params: { companyId, ...params } })).data });
}

export function useCrmCustomers(companyId: string) {
  return useCrmRows(companyId, 'customers', '/crm/customers');
}

export function useCrmStages(companyId: string) {
  return useCrmRows(companyId, 'stages', '/crm/pipeline/stages');
}
