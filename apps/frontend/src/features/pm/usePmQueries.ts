import { useQuery } from '@tanstack/react-query';
import { api } from '../../core/http/apiClient';

export type PmRow = Record<string, unknown>;

export type TaskFilters = {
  projectId: string; sprintId: string; statusId: string; assigneeId: string; priority: string;
  from: string; to: string;
};

export const emptyTaskFilters: TaskFilters = {
  projectId: '', sprintId: '', statusId: '', assigneeId: '', priority: '', from: '', to: '',
};

export function usePmTasks(companyId: string, filters: TaskFilters) {
  return useQuery({ queryKey: ['pm-tasks', companyId, filters], enabled: !!companyId,
    queryFn: async () => {
      const params: Record<string, string> = { companyId };
      if (filters.projectId) params.projectId = filters.projectId;
      if (filters.sprintId) params.sprintId = filters.sprintId;
      if (filters.statusId) params.statusId = filters.statusId;
      if (filters.assigneeId) params.assigneeId = filters.assigneeId;
      if (filters.priority) params.priority = filters.priority;
      if (filters.from) params.from = filters.from;
      if (filters.to) params.to = filters.to;
      return (await api.get<PmRow[]>('/pm/tasks', { params })).data;
    } });
}

export function usePmProjects(companyId: string) {
  return useQuery({ queryKey: ['pm-projects', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/projects', { params: { companyId } })).data });
}

export function usePmWorkflowStatuses(companyId: string, workflowType = 'TASK') {
  return useQuery({ queryKey: ['pm-workflow-statuses', companyId, workflowType], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/workflow/statuses', { params: { companyId, workflowType } })).data });
}

export function usePmSprints(companyId: string, projectId?: string) {
  return useQuery({ queryKey: ['pm-sprints', companyId, projectId ?? ''], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/sprints', { params: { companyId, projectId: projectId || undefined } })).data });
}

export function usePmEmployees(companyId: string) {
  return useQuery({ queryKey: ['pm-employees', companyId], enabled: !!companyId, retry: false,
    queryFn: async () => (await api.get<PmRow[]>('/hr/employees', { params: { companyId } })).data });
}
