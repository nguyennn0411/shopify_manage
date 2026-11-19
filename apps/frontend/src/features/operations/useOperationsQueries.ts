import {useQuery} from '@tanstack/react-query';
import {api} from '../../core/http/apiClient';
export type OperationsRow=Record<string,unknown>;
export function useOperationsRows(companyId:string,key:string,endpoint:string,params:Record<string,unknown>={},enabled=true){return useQuery({queryKey:['operations',key,companyId,params],enabled:!!companyId&&enabled,queryFn:async()=>(await api.get<OperationsRow[]>(endpoint,{params:{companyId,...params}})).data});}
export const useOperationsReference=(companyId:string,kind:string)=>useOperationsRows(companyId,`reference-${kind}`,`/operations/references/${kind}`);
