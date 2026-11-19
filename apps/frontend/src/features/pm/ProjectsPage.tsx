import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, Target } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { usePmEmployees, usePmProjects, type PmRow } from './usePmQueries';

type Created = { id: string; status: string };
const STATUS_OPTIONS = ['PLANNED', 'ACTIVE', 'ON_HOLD', 'CLOSED', 'CANCELLED'] as const;
const STATUS_LABELS: Record<string, string> = { PLANNED: 'Đã lên kế hoạch', ACTIVE: 'Đang thực hiện', ON_HOLD: 'Tạm dừng', CLOSED: 'Đã đóng', CANCELLED: 'Đã huỷ' };

const projectSchema = z.object({
  code: z.string().trim().max(60).optional(),
  name: z.string().trim().min(1, 'Tên dự án là bắt buộc').max(255),
  ownerEmployeeId: z.string().optional(),
  objective: z.string().trim().max(4000).optional(),
  scope: z.string().trim().max(4000).optional(),
  budget: z.number().min(0).optional(),
  currency: z.string().trim().max(3).optional(),
  status: z.enum(STATUS_OPTIONS),
  startsOn: z.string().optional(),
  endsOn: z.string().optional(),
  riskLevel: z.enum(['LOW', 'MEDIUM', 'HIGH']),
});
type ProjectForm = z.infer<typeof projectSchema>;

function text(row: PmRow, key: string): string { const v = row[key]; return v === null || v === undefined ? '' : String(v); }
function num(row: PmRow, key: string): number { const n = Number(row[key]); return Number.isFinite(n) ? n : 0; }

export function ProjectsPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState('');
  const [memberEmployeeId, setMemberEmployeeId] = useState('');
  const [memberRole, setMemberRole] = useState('MEMBER');
  const [memberCostRate, setMemberCostRate] = useState('');
  const [milestoneTitle, setMilestoneTitle] = useState('');
  const [milestoneDue, setMilestoneDue] = useState('');

  const projects = usePmProjects(companyId);
  const employees = usePmEmployees(companyId);
  const selected = projects.data?.find(row => text(row, 'id') === selectedId) ?? null;
  const members = useQuery({ queryKey: ['pm-project-members', companyId, selectedId], enabled: !!selectedId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/projects/${selectedId}/members`, { params: { companyId } })).data });
  const milestones = useQuery({ queryKey: ['pm-milestones', companyId, selectedId], enabled: !!selectedId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/projects/${selectedId}/milestones`, { params: { companyId } })).data });
  const risks = useQuery({ queryKey: ['pm-risks', companyId, selectedId], enabled: !!selectedId,
    queryFn: async () => (await api.get<PmRow[]>(`/pm/projects/${selectedId}/risks`, { params: { companyId } })).data });

  const create = useMutation({ mutationFn: async (values: ProjectForm) =>
      (await api.post<Created>('/pm/projects', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-projects', companyId] }) });
  const decide = useMutation({ mutationFn: async (input: { projectId: string; status: string }) =>
      (await api.post<Created>(`/pm/projects/${input.projectId}/decision`, { status: input.status }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-projects', companyId] }) });
  const addMember = useMutation({ mutationFn: async () =>
      (await api.post<Created>(`/pm/projects/${selectedId}/members`, { employeeId: memberEmployeeId, role: memberRole, costRate: memberCostRate ? Number(memberCostRate) : undefined }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-project-members', companyId, selectedId] }) });
  const addMilestone = useMutation({ mutationFn: async () =>
      (await api.post<Created>(`/pm/projects/${selectedId}/milestones`, { title: milestoneTitle, dueDate: milestoneDue }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-milestones', companyId, selectedId] }) });

  const form = useForm<ProjectForm>({ resolver: zodResolver(projectSchema),
    defaultValues: { code: '', name: '', ownerEmployeeId: '', objective: '', scope: '', budget: undefined, currency: '',
      status: 'PLANNED', startsOn: '', endsOn: '', riskLevel: 'LOW' } });

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><Target /></span><h1>Chọn công ty</h1><p>Chọn workspace để xem dự án.</p></section>;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / projects</span><h1>Quản trị dự án</h1><p>Danh sách dự án, thành viên, mục tiêu, phạm vi, ngân sách, tiến độ và mốc quan trọng.</p></div>

    <section className="panel table-panel"><h2>Danh sách dự án</h2>
      {projects.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Mã</th><th>Tên</th><th>Phòng ban</th><th>Ngân sách</th><th>Tiến độ</th><th>Rủi ro</th><th>Thành viên</th><th>Công việc</th><th>Trạng thái</th></tr></thead><tbody>
        {projects.data.map(row => <tr key={text(row, 'id')} onClick={() => setSelectedId(text(row, 'id'))} style={{ cursor: 'pointer' }}>
          <td>{text(row, 'code')}</td><td><strong>{text(row, 'name')}</strong></td><td>{text(row, 'departmentName') || '—'}</td>
          <td>{row.budget !== null && row.budget !== undefined ? `${num(row, 'budget')} ${text(row, 'currency')}` : '—'}</td>
          <td><div className="load-bar"><div className="load-fill" style={{ width: `${num(row, 'progressPercent')}%` }} /></div></td>
          <td>{text(row, 'riskLevel')}</td><td>{num(row, 'memberCount')}</td><td>{num(row, 'taskCount')}</td>
          <td><span className="status-pill">{STATUS_LABELS[text(row, 'status')] ?? text(row, 'status')}</span></td></tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có dự án nào.</p></div>}
    </section>

    {can('pm.project.write') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Tạo dự án</strong><p>Mục tiêu, phạm vi, ngân sách và thời gian dự kiến.</p></div></div>
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => create.mutate(values))}>
        <label>Mã (tuỳ chọn)<input {...form.register('code')} /></label>
        <label>Tên dự án<input {...form.register('name')} /></label>
        <label>Người phụ trách<select {...form.register('ownerEmployeeId')}><option value="">Chưa gán</option>{employees.data?.map(employee => <option key={text(employee, 'id')} value={text(employee, 'id')}>{text(employee, 'fullName')}</option>)}</select></label>
        <label>Ngân sách<input type="number" min="0" step="0.01" {...form.register('budget', { valueAsNumber: true })} /></label>
        <label>Đơn vị tiền tệ<input {...form.register('currency')} placeholder="VND" /></label>
        <label>Rủi ro<select {...form.register('riskLevel')}>{['LOW', 'MEDIUM', 'HIGH'].map(level => <option key={level} value={level}>{level}</option>)}</select></label>
        <label>Trạng thái<select {...form.register('status')}>{STATUS_OPTIONS.map(status => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}</select></label>
        <label>Bắt đầu<input type="date" {...form.register('startsOn')} /></label>
        <label>Kết thúc<input type="date" {...form.register('endsOn')} /></label>
        <label className="wide-field">Mục tiêu<textarea rows={2} {...form.register('objective')} /></label>
        <label className="wide-field">Phạm vi<textarea rows={2} {...form.register('scope')} /></label>
        <div className="form-actions"><button className="primary" disabled={create.isPending}>Tạo dự án</button>
          {create.isSuccess && <span className="success-text">Đã tạo dự án.</span>}{create.error && <span className="form-error">{create.error.message}</span>}</div>
      </form>
    </section>}

    {selected && <section className="panel">
      <div className="quick-action-title"><div><strong>{text(selected, 'code')} · {text(selected, 'name')}</strong>
        <p>{STATUS_LABELS[text(selected, 'status')] ?? text(selected, 'status')}</p></div>
        {can('pm.project.write') && <div className="button-row">
          {STATUS_OPTIONS.filter(status => status !== text(selected, 'status')).map(status =>
            <button key={status} className="secondary" onClick={() => decide.mutate({ projectId: text(selected, 'id'), status })}>{STATUS_LABELS[status]}</button>)}
        </div>}
      </div>
      <div className="private-grid">
        <div><dt>Ngân sách</dt><dd>{row_money(selected)}</dd></div>
        <div><dt>Chi phí thực tế</dt><dd>{selected.actualCost !== null && selected.actualCost !== undefined ? num(selected, 'actualCost') : '—'}</dd></div>
        <div><dt>Lợi nhuận</dt><dd>{selected.profit !== null && selected.profit !== undefined ? num(selected, 'profit') : '—'}</dd></div>
        <div><dt>Tiến độ</dt><dd>{num(selected, 'progressPercent')}%</dd></div>
      </div>
      {can('pm.project.write') && <div className="button-row"><input value={memberEmployeeId} onChange={event => setMemberEmployeeId(event.target.value)} placeholder="Chọn nhân viên" list="pm-employee-list" />
        <input value={memberCostRate} onChange={event => setMemberCostRate(event.target.value)} placeholder="Đơn giá/giờ (tuỳ chọn)" />
        <select value={memberRole} onChange={event => setMemberRole(event.target.value)}><option value="OWNER">Quản lý</option><option value="LEAD">Trưởng nhóm</option><option value="MEMBER">Thành viên</option></select>
        <button className="secondary" disabled={!memberEmployeeId || addMember.isPending} onClick={() => addMember.mutate()}>Thêm thành viên</button>
        <datalist id="pm-employee-list">{employees.data?.map(employee => <option key={text(employee, 'id')} value={text(employee, 'id')}>{text(employee, 'fullName')}</option>)}</datalist></div>}
      {can('pm.project.write') && <div className="button-row"><input value={milestoneTitle} onChange={event => setMilestoneTitle(event.target.value)} placeholder="Mốc quan trọng" />
        <input type="date" value={milestoneDue} onChange={event => setMilestoneDue(event.target.value)} />
        <button className="secondary" disabled={!milestoneTitle.trim() || !milestoneDue || addMilestone.isPending} onClick={() => addMilestone.mutate()}>Thêm mốc</button></div>}
      <h2>Mốc quan trọng</h2>
      {milestones.data?.length ? <div className="button-row">{milestones.data.map(row => <span key={text(row, 'id')} className="balance-row"><span>{text(row, 'title')} · {text(row, 'due_date')} · {text(row, 'status')}</span></span>)}</div>
        : <p className="table-message">Chưa có mốc nào.</p>}
      <h2>Rủi ro</h2>
      {risks.data?.length ? <div className="button-row">{risks.data.map(row => <span key={text(row, 'id')} className="balance-row"><span>{text(row, 'title')} · {text(row, 'likelihood')}/{text(row, 'impact')} · {text(row, 'status')}</span></span>)}</div>
        : <p className="table-message">Chưa có rủi ro nào.</p>}
      <h2>Thành viên</h2>
      {members.data?.length ? <div className="button-row">{members.data.map(row => <span key={text(row, 'employee_id')} className="balance-row"><span>{text(row, 'full_name')} · {text(row, 'role')}{row.cost_rate ? ` · ${num(row, 'cost_rate')}/giờ` : ''}</span></span>)}</div>
        : <p className="table-message">Chưa có thành viên nào.</p>}
    </section>}
  </>;
}

function row_money(row: PmRow): string {
  if (row.budget === null || row.budget === undefined) return '—';
  return `${num(row, 'budget')} ${text(row, 'currency')}`;
}
