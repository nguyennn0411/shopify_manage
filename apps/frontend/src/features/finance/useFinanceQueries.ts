import { useQuery } from '@tanstack/react-query';
import { api } from '../../core/http/apiClient';

export type FinanceRow = Record<string, unknown>;

export function useFinanceRows(companyId:string,key:string,endpoint:string,params:Record<string,unknown>={},enabled=true){
  return useQuery({queryKey:['finance',key,companyId,params],enabled:!!companyId&&enabled,
    queryFn:async()=>(await api.get<FinanceRow[]>(endpoint,{params:{companyId,...params}})).data});
}

export const useFinanceCategories=(companyId:string)=>useFinanceRows(companyId,'categories','/finance/categories');
export const useFinanceReference=(companyId:string,kind:string)=>useFinanceRows(companyId,`reference-${kind}`,`/finance/references/${kind}`);
