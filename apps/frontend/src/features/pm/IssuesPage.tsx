import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bug, Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { usePmEmployees, usePmProjects, usePmTasks, type PmRow } from './usePmQueries';

type Created = { id: string; status: string };
const ISSUE_TYPES: Record<string, string> = { BUG: 'Lỗi', ISSUE: 'Vấn đề', BLOCKER: 'Điểm nghẽn', CHANGE_REQUEST: 'Yêu cầu thay đổi' };
const SEVERITIES: Record<string, string> = { CRITICAL: 'Nghiêm trọng', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' };
const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED'] as const;

const issueSchema = z.object({
  type: z.enum(['BUG', 'ISSUE', 'BLOCKER', 'CHANGE_REQUEST']),
  severity: z.enum(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']),
  title: z.string().trim().min(1, 'Tiêu đề là bắt buộc').max(255),
  description: z.string().trim().max(4000).optional(),
  projectId: z.string().optional(),
  taskId: z.string().optional(),
  assigneeEmployeeId: z.string().optional(),
});
type IssueForm = z.infer<typeof issueSchema>;

function text(row: PmRow, key: string): string { const v = row[key]; return v === null || v === undefined ? '' : String(v); }

export function IssuesPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [severityFilter, setSeverityFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [selectedId, setSelectedId] = useState('');
  const [resolveNote, setResolveNote] = useState('');

  const projects = usePmProjects(companyId);
  const tasks = usePmTasks(companyId, { projectId: '', sprintId: '', statusId: '', assigneeId: '', priority: '', from: '', to: '' });
  const employees = usePmEmployees(companyId);
  const issues = useQuery({ queryKey: ['pm-issues', companyId, severityFilter, statusFilter], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/issues', {
      params: { companyId, severity: severityFilter || undefined, status: statusFilter || undefined } })).data });
  const history = useQuery({ queryKey: ['pm-issue-history', companyId, selectedId], enabled: !!selectedId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/issues/${selectedId}/history`, { params: { companyId } })).data });

  const create = useMutation({ mutationFn: async (values: IssueForm) =>
      (await api.post<Created>('/pm/issues', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-issues', companyId] }) });
  const decide = useMutation({ mutationFn: async (input: { issueId: string; status: string }) =>
      (await api.post<Created>(`/pm/issues/${input.issueId}/decision`, { status: input.status }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-issues', companyId] }) });
  const resolve = useMutation({ mutationFn: async (input: { issueId: string; resolutionNote: string }) =>
      (await api.post<Created>(`/pm/issues/${input.issueId}/resolve`, { resolutionNote: input.resolutionNote }, { params: { companyId } })).data,
    onSuccess: () => { setResolveNote(''); queryClient.invalidateQueries({ queryKey: ['pm-issues', companyId] }); } });

  const form = useForm<IssueForm>({ resolver: zodResolver(issueSchema),
    defaultValues: { type: 'BUG', severity: 'MEDIUM', title: '', description: '', projectId: '', taskId: '', assigneeEmployeeId: '' } });

  const selected = issues.data?.find(row => text(row, 'id') === selectedId) ?? null;

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><Bug /></span><h1>Chọn công ty</h1><p>Chọn workspace để xem sự cố.</p></section>;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / issues</span><h1>Quản lý sự cố</h1><p>Lỗi, vấn đề, điểm nghẽn và yêu cầu thay đổi; mức nghiêm trọng, người xử lý và lịch sử khắc phục.</p></div>

    {can('pm.issue.write') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Định nghĩa sự cố</strong><p>Loại, mức nghiêm trọng, liên kết dự án/công việc và người xử lý.</p></div></div>
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => create.mutate({
        type: values.type, severity: values.severity, title: values.title, description: values.description || undefined,
        projectId: values.projectId || undefined, taskId: values.taskId || undefined,
        assigneeEmployeeId: values.assigneeEmployeeId || undefined,
      }))}>
        <label>Loại<select {...form.register('type')}>{Object.entries(ISSUE_TYPES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label>Mức nghiêm trọng<select {...form.register('severity')}>{Object.entries(SEVERITIES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
        <label className="wide-field">Tiêu đề<input {...form.register('title')} /></label>
        <label>Dự án<select {...form.register('projectId')}><option value="">Không</option>{projects.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'name')}</option>)}</select></label>
        <label>Công việc<select {...form.register('taskId')}><option value="">Không</option>{tasks.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'title')}</option>)}</select></label>
        <label>Người xử lý<select {...form.register('assigneeEmployeeId')}><option value="">Chưa gán</option>{employees.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'fullName')}</option>)}</select></label>
        <label className="wide-field">Mô tả<textarea rows={2} {...form.register('description')} /></label>
        <div className="form-actions"><button className="primary" disabled={create.isPending}>Tạo sự cố</button>
          {create.isSuccess && <span className="success-text">Đã tạo sự cố.</span>}{create.error && <span className="form-error">{create.error.message}</span>}</div>
      </form>
    </section>}

    <section className="panel table-panel"><div className="button-row">
      <label>Mức nghiêm trọng<select value={severityFilter} onChange={event => setSeverityFilter(event.target.value)}>
        <option value="">Tất cả</option>{Object.entries(SEVERITIES).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <label>Trạng thái<select value={statusFilter} onChange={event => setStatusFilter(event.target.value)}>
        <option value="">Tất cả</option>{STATUS_OPTIONS.map(status => <option key={status} value={status}>{status}</option>)}</select></label>
    </div>
      <h2>Sự cố</h2>
      {issues.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Mã</th><th>Tiêu đề</th><th>Loại</th><th>Nghiêm trọng</th><th>Xử lý bởi</th><th>Liên kết</th><th>Trạng thái</th>{can('pm.issue.write') && <th>Thao tác</th>}</tr></thead><tbody>
        {issues.data.map(row => <tr key={text(row, 'id')} onClick={() => setSelectedId(text(row, 'id'))} style={{ cursor: 'pointer' }}>
          <td>{text(row, 'code')}</td><td><strong>{text(row, 'title')}</strong></td>
          <td>{ISSUE_TYPES[text(row, 'type')] ?? text(row, 'type')}</td>
          <td><span className={`priority-pill p-${text(row, 'severity').toLowerCase()}`}>{SEVERITIES[text(row, 'severity')] ?? text(row, 'severity')}</span></td>
          <td>{text(row, 'assignee_name') || 'Chưa gán'}</td>
          <td>{text(row, 'project_code') || text(row, 'task_code') || '—'}</td>
          <td><span className="status-pill">{text(row, 'status')}</span></td>
          {can('pm.issue.write') ? <td onClick={event => event.stopPropagation()}><div className="button-row">
            {text(row, 'status') !== 'IN_PROGRESS' && <button className="secondary" onClick={() => decide.mutate({ issueId: text(row, 'id'), status: 'IN_PROGRESS' })}>Xử lý</button>}
            {text(row, 'status') !== 'RESOLVED' && <button className="secondary" onClick={() => decide.mutate({ issueId: text(row, 'id'), status: 'RESOLVED' })}>Khắc phục</button>}
          </div></td> : null}</tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có sự cố nào.</p></div>}
    </section>

    {selected && <section className="panel"><div className="quick-action-title"><div>
      <strong>{text(selected, 'code')} · {text(selected, 'title')}</strong>
      <p>{ISSUE_TYPES[text(selected, 'type')] ?? text(selected, 'type')} · {SEVERITIES[text(selected, 'severity')] ?? text(selected, 'severity')} · {text(selected, 'status')}</p>
    </div><button className="secondary" onClick={() => setSelectedId('')}>Đóng</button></div>
      <p>{text(selected, 'description') || 'Chưa có mô tả.'}</p>
      {text(selected, 'resolution_note') && <div className="private-grid"><div><dt>Lịch sử khắc phục</dt><dd>{text(selected, 'resolution_note')}</dd></div>
        {text(selected, 'resolved_at') && <div><dt>Thời điểm</dt><dd>{text(selected, 'resolved_at')}</dd></div>}</div>}
      {can('pm.issue.write') && text(selected, 'status') !== 'RESOLVED' && <div className="button-row">
        <input value={resolveNote} onChange={event => setResolveNote(event.target.value)} placeholder="Ghi chú khắc phục" style={{ flex: 1 }} />
        <button className="secondary" disabled={!resolveNote.trim() || resolve.isPending}
          onClick={() => resolve.mutate({ issueId: text(selected, 'id'), resolutionNote: resolveNote.trim() })}>Ghi khắc phục</button>
      </div>}
      <h2>Lịch sử hoạt động</h2>
      {history.data?.length ? history.data.map(row => <p key={text(row, 'id')} className="table-message">
        {text(row, 'created_at')} · {text(row, 'actor_subject')} · {text(row, 'action')}</p>)
        : <p className="table-message">Chưa có hoạt động nào.</p>}
    </section>}
  </>;
}
