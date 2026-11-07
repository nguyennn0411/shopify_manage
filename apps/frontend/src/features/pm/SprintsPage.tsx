import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { GitBranch, Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { usePmProjects, usePmSprints, usePmTasks, usePmWorkflowStatuses, type PmRow } from './usePmQueries';

type Created = { id: string; status: string };
const SPRINT_STATUS_LABELS: Record<string, string> = { PLANNED: 'Đã lên kế hoạch', ACTIVE: 'Đang chạy', CLOSED: 'Đã đóng' };

const sprintSchema = z.object({
  projectId: z.string().min(1, 'Dự án là bắt buộc'),
  name: z.string().trim().min(1, 'Tên sprint là bắt buộc').max(180),
  goal: z.string().trim().max(4000).optional(),
  startsOn: z.string().min(1),
  endsOn: z.string().min(1),
}).refine(value => value.endsOn >= value.startsOn, { path: ['endsOn'], message: 'Ngày kết thúc không hợp lệ' });
type SprintForm = z.infer<typeof sprintSchema>;

const statusSchema = z.object({
  code: z.string().trim().min(1, 'Mã là bắt buộc').max(40),
  name: z.string().trim().min(1, 'Tên là bắt buộc').max(120),
  sortOrder: z.number().int().min(0),
  color: z.string().trim().max(20).optional(),
  isDone: z.boolean(),
});
type StatusForm = z.infer<typeof statusSchema>;

function text(row: PmRow, key: string): string { const v = row[key]; return v === null || v === undefined ? '' : String(v); }
function num(row: PmRow, key: string): number { const n = Number(row[key]); return Number.isFinite(n) ? n : 0; }

export function SprintsPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [projectFilter, setProjectFilter] = useState('');
  const [backlogFilter, setBacklogFilter] = useState('');

  const projects = usePmProjects(companyId);
  const sprints = usePmSprints(companyId, projectFilter || undefined);
  const workflow = usePmWorkflowStatuses(companyId, 'TASK');
  const backlog = usePmTasks(companyId, {
    projectId: projectFilter, sprintId: '', statusId: backlogFilter, assigneeId: '', priority: '', from: '', to: '',
  });

  const createSprint = useMutation({ mutationFn: async (values: SprintForm) =>
      (await api.post<Created>('/pm/sprints', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-sprints', companyId] }) });
  const decideSprint = useMutation({ mutationFn: async (input: { sprintId: string; status: string }) =>
      (await api.post<Created>(`/pm/sprints/${input.sprintId}/decision`, { status: input.status }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-sprints', companyId] }) });
  const createStatus = useMutation({ mutationFn: async (values: StatusForm) =>
      (await api.post<Created>('/pm/workflow/statuses', { workflowType: 'TASK', ...values }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-workflow-statuses', companyId] }) });

  const sprintForm = useForm<SprintForm>({ resolver: zodResolver(sprintSchema),
    defaultValues: { projectId: '', name: '', goal: '', startsOn: '', endsOn: '' } });
  const statusForm = useForm<StatusForm>({ resolver: zodResolver(statusSchema),
    defaultValues: { code: '', name: '', sortOrder: 1, color: '', isDone: false } });

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><GitBranch /></span><h1>Chọn công ty</h1><p>Chọn workspace để xem sprint.</p></section>;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / sprints</span><h1>Sprint & quy trình</h1><p>Sprint, backlog và trạng thái công việc tùy chỉnh cho Kanban.</p></div>

    <section className="panel table-panel"><div className="button-row">
      <label>Dự án<select value={projectFilter} onChange={event => setProjectFilter(event.target.value)}>
        <option value="">Tất cả dự án</option>{projects.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'name')}</option>)}</select></label>
    </div>
      <h2>Sprint</h2>
      {sprints.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Sprint</th><th>Dự án</th><th>Thời gian</th><th>Công việc</th><th>Trạng thái</th>{can('pm.sprint.write') && <th>Thao tác</th>}</tr></thead><tbody>
        {sprints.data.map(row => <tr key={text(row, 'id')}>
          <td><strong>{text(row, 'name')}</strong><small>{text(row, 'goal') || 'Chưa có mục tiêu'}</small></td>
          <td>{text(row, 'project_code')} · {text(row, 'project_name')}</td>
          <td>{text(row, 'starts_on')} → {text(row, 'ends_on')}</td>
          <td>{num(row, 'task_count')}</td>
          <td><span className="status-pill">{SPRINT_STATUS_LABELS[text(row, 'status')] ?? text(row, 'status')}</span></td>
          {can('pm.sprint.write') ? <td><div className="button-row">
            {text(row, 'status') !== 'ACTIVE' && <button className="secondary" onClick={() => decideSprint.mutate({ sprintId: text(row, 'id'), status: 'ACTIVE' })}>Bắt đầu</button>}
            {text(row, 'status') !== 'CLOSED' && <button className="secondary" onClick={() => decideSprint.mutate({ sprintId: text(row, 'id'), status: 'CLOSED' })}>Đóng</button>}
          </div></td> : null}</tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có sprint nào.</p></div>}
    </section>

    {can('pm.sprint.write') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Tạo sprint</strong><p>Gắn vào dự án, đặt mục tiêu và khoảng thời gian.</p></div></div>
      <form className="form-grid compact-form" onSubmit={sprintForm.handleSubmit(values => createSprint.mutate(values))}>
        <label>Dự án<select {...sprintForm.register('projectId')}><option value="">Chọn dự án</option>{projects.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'name')}</option>)}</select></label>
        <label>Tên sprint<input {...sprintForm.register('name')} /></label>
        <label>Bắt đầu<input type="date" {...sprintForm.register('startsOn')} /></label>
        <label>Kết thúc<input type="date" {...sprintForm.register('endsOn')} /></label>
        <label className="wide-field">Mục tiêu<textarea rows={2} {...sprintForm.register('goal')} /></label>
        <div className="form-actions"><button className="primary" disabled={createSprint.isPending}>Tạo sprint</button>
          {createSprint.isSuccess && <span className="success-text">Đã tạo sprint.</span>}{createSprint.error && <span className="form-error">{createSprint.error.message}</span>}</div>
      </form>
    </section>}

    <section className="panel table-panel"><div className="button-row">
      <label>Trạng thái<select value={backlogFilter} onChange={event => setBacklogFilter(event.target.value)}>
        <option value="">Tất cả trạng thái</option>{workflow.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'name')}</option>)}</select></label>
    </div>
      <h2>Backlog</h2>
      {backlog.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Mã</th><th>Tiêu đề</th><th>Dự án</th><th>Phụ trách</th><th>Ưu tiên</th><th>Hạn</th></tr></thead><tbody>
        {backlog.data.map(row => <tr key={text(row, 'id')}>
          <td>{text(row, 'code')}</td><td><strong>{text(row, 'title')}</strong></td>
          <td>{text(row, 'project_code') || '—'}</td><td>{text(row, 'assignee_name') || 'Chưa gán'}</td>
          <td>{text(row, 'priority')}</td><td>{text(row, 'due_date') || '—'}</td></tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có công việc trong backlog.</p></div>}
    </section>

    <section className="panel"><h2>Trạng thái công việc</h2>
      <p>Các cột Kanban và trạng thái tuỳ chỉnh của công ty.</p>
      {workflow.data?.map(row => <span key={text(row, 'id')} className="balance-row"><span style={{ color: text(row, 'color') || undefined }}>●</span> {text(row, 'name')} ({text(row, 'code')}){text(row, 'is_done') === 'true' ? ' · hoàn thành' : ''}</span>)}
      {workflow.data?.length === 0 && <p className="table-message">Chưa có trạng thái nào.</p>}
    </section>

    {can('pm.workflow.manage') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Thêm trạng thái tuỳ chỉnh</strong><p>Hiển thị làm cột mới trên Kanban.</p></div></div>
      <form className="form-grid compact-form" onSubmit={statusForm.handleSubmit(values => createStatus.mutate(values))}>
        <label>Mã<input {...statusForm.register('code')} placeholder="DONE" /></label>
        <label>Tên<input {...statusForm.register('name')} /></label>
        <label>Thứ tự<input type="number" min="0" step="1" {...statusForm.register('sortOrder', { valueAsNumber: true })} /></label>
        <label>Màu (hex)<input {...statusForm.register('color')} placeholder="#5b8def" /></label>
        <label>Đánh dấu là trạng thái hoàn thành<input type="checkbox" {...statusForm.register('isDone')} style={{ width: 'auto' }} /></label>
        <div className="form-actions"><button className="primary" disabled={createStatus.isPending}>Thêm trạng thái</button>
          {createStatus.isSuccess && <span className="success-text">Đã thêm trạng thái.</span>}{createStatus.error && <span className="form-error">{createStatus.error.message}</span>}</div>
      </form>
    </section>}
  </>;
}
