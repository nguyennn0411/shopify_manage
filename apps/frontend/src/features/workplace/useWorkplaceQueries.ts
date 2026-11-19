import {useQuery} from '@tanstack/react-query';
import {api} from '../../core/http/apiClient';
export type WorkplaceRow=Record<string,unknown>;
export function useWorkplaceRows(companyId:string,key:string,endpoint:string,params:Record<string,unknown>={},enabled=true){return useQuery({queryKey:['workplace',key,companyId,params],enabled:!!companyId&&enabled,queryFn:async()=>(await api.get<WorkplaceRow[]>(endpoint,{params:{companyId,...params}})).data});}
export const useWorkplaceReference=(companyId:string,kind:string)=>useWorkplaceRows(companyId,`reference-${kind}`,`/workplace/references/${kind}`);
