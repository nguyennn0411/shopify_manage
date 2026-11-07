import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { usePmEmployees, usePmProjects, usePmTasks, type PmRow } from './usePmQueries';

type Created = { id: string; status: string };
const STATUS_LABELS: Record<string, string> = { DRAFT: 'Bản nháp', SUBMITTED: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' };

const timesheetSchema = z.object({
  employeeId: z.string().optional(),
  taskId: z.string().optional(),
  projectId: z.string().optional(),
  workDate: z.string().min(1, 'Ngày làm việc là bắt buộc'),
  hours: z.number().min(0.25).max(24),
  note: z.string().trim().max(500).optional(),
}).refine(value => !value.taskId && !value.projectId ? false : true,
  { message: 'Chọn một công việc hoặc một dự án' });
type TimesheetForm = z.infer<typeof timesheetSchema>;

function text(row: PmRow, key: string): string { const v = row[key]; return v === null || v === undefined ? '' : String(v); }
function num(row: PmRow, key: string): number { const n = Number(row[key]); return Number.isFinite(n) ? n : 0; }

export function TimesheetsPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ from: '', to: '' });

  const employees = usePmEmployees(companyId);
  const projects = usePmProjects(companyId);
  const tasks = usePmTasks(companyId, { projectId: '', sprintId: '', statusId: '', assigneeId: '', priority: '', from: '', to: '' });
  const entries = useQuery({ queryKey: ['pm-timesheets', companyId, range], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/timesheets', {
      params: { companyId, from: range.from || undefined, to: range.to || undefined } })).data });

  const create = useMutation({ mutationFn: async (values: TimesheetForm) =>
      (await api.post<Created>('/pm/timesheets', {
        employeeId: values.employeeId || undefined, taskId: values.taskId || undefined,
        projectId: values.projectId || undefined, workDate: values.workDate, hours: values.hours,
        note: values.note || undefined,
      }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-timesheets', companyId] }) });
  const review = useMutation({ mutationFn: async (input: { entryId: string; decision: string }) =>
      (await api.post<Created>(`/pm/timesheets/${input.entryId}/review`, { decision: input.decision }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-timesheets', companyId] }) });

  const form = useForm<TimesheetForm>({ resolver: zodResolver(timesheetSchema),
    defaultValues: { employeeId: '', taskId: '', projectId: '', workDate: '', hours: 1, note: '' } });

  const comparison = (entries.data ?? []).reduce((acc, row) => {
    const key = text(row, 'task_code') || text(row, 'project_code') || 'UNASSIGNED';
    const item = acc.get(key) ?? { label: key, estimated: 0, actual: 0, logged: 0 };
    item.actual += text(row, 'status') === 'APPROVED' ? num(row, 'hours') : 0;
    item.logged += num(row, 'hours');
    acc.set(key, item);
    return acc;
  }, new Map<string, { label: string; estimated: number; actual: number; logged: number }>());
  for (const row of (tasks.data ?? [])) {
    const code = text(row, 'task_code') || text(row, 'code');
    const item = comparison.get(code) ?? { label: code, estimated: 0, actual: 0, logged: 0 };
    item.estimated = num(row, 'estimated_hours');
    comparison.set(code, item);
  }

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><CalendarClock /></span><h1>Chọn công ty</h1><p>Chọn workspace để ghi thời gian.</p></section>;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / timesheets</span><h1>Timesheet</h1><p>Ghi thời gian theo công việc, dự án và nhân viên; so sánh thực tế với dự kiến.</p></div>

    {can('pm.timesheet.write') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Ghi thời gian</strong><p>Công việc hoặc dự án, số giờ và ghi chú.</p></div></div>
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => create.mutate(values))}>
        <label>Nhân viên<select {...form.register('employeeId')}><option value="">Chính tôi</option>{employees.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'fullName')}</option>)}</select></label>
        <label>Công việc<select {...form.register('taskId')}><option value="">Không</option>{tasks.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'title')}</option>)}</select></label>
        <label>Dự án<select {...form.register('projectId')}><option value="">Không</option>{projects.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'name')}</option>)}</select></label>
        <label>Ngày làm việc<input type="date" {...form.register('workDate')} /></label>
        <label>Số giờ<input type="number" min="0.25" max="24" step="0.25" {...form.register('hours', { valueAsNumber: true })} /></label>
        <label className="wide-field">Ghi chú<textarea rows={1} {...form.register('note')} /></label>
        <div className="form-actions"><button className="primary" disabled={create.isPending}>Ghi thời gian</button>
          {create.isSuccess && <span className="success-text">Đã ghi nhận.</span>}{create.error && <span className="form-error">{create.error.message}</span>}</div>
      </form>
    </section>}

    <section className="panel table-panel"><div className="button-row">
      <label>Từ<input type="date" value={range.from} onChange={event => setRange(value => ({ ...value, from: event.target.value }))} /></label>
      <label>Đến<input type="date" value={range.to} onChange={event => setRange(value => ({ ...value, to: event.target.value }))} /></label>
    </div>
      <h2>Điểm thời gian</h2>
      {entries.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Nhân viên</th><th>Công việc</th><th>Dự án</th><th>Ngày</th><th>Giờ</th><th>Trạng thái</th>{can('pm.timesheet.approve') && <th>Duyệt</th>}</tr></thead><tbody>
        {entries.data.map(row => <tr key={text(row, 'id')}>
          <td><strong>{text(row, 'employee_name')}</strong><small>{text(row, 'employee_code')}</small></td>
          <td>{text(row, 'task_code') || '—'} {text(row, 'task_title')}</td>
          <td>{text(row, 'project_code') || '—'}</td>
          <td>{text(row, 'work_date')}</td>
          <td>{num(row, 'hours')}</td>
          <td><span className="status-pill">{STATUS_LABELS[text(row, 'status')] ?? text(row, 'status')}</span></td>
          {can('pm.timesheet.approve') ? <td><div className="button-row">
            {text(row, 'status') !== 'APPROVED' && <button className="secondary" onClick={() => review.mutate({ entryId: text(row, 'id'), decision: 'APPROVED' })}>Duyệt</button>}
            {text(row, 'status') !== 'REJECTED' && <button className="secondary" onClick={() => review.mutate({ entryId: text(row, 'id'), decision: 'REJECTED' })}>Từ chối</button>}
          </div></td> : null}</tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có điểm thời gian.</p></div>}
    </section>

    <section className="panel"><h2>Thực tế so với dự kiến</h2>
      <p>Số giờ đã ghi so với giờ dự kiến của công việc.</p>
      {comparison.size ? <div className="table-scroll"><table><thead><tr>
        <th>Công việc</th><th>Dự kiến (giờ)</th><th>Đã ghi (giờ)</th><th>Đã duyệt (giờ)</th><th>Chênh lệch</th></tr></thead><tbody>
        {[...comparison.values()].map(item => {
          const variance = item.logged - item.estimated;
          return <tr key={item.label}><td><strong>{item.label}</strong></td><td>{item.estimated}</td><td>{item.logged}</td><td>{item.actual}</td>
            <td className={variance > 0 ? 'overdue-text' : ''}>{variance > 0 ? `+${variance}` : variance}</td></tr>;
        })}</tbody></table></div> : <div className="empty-table"><p>Chưa có dữ liệu so sánh.</p></div>}
    </section>
  </>;
}
