import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const caseSchema = z.object({ employeeId: z.string().uuid(), startDate: z.string().min(1), mentorEmployeeId: z.string().trim().max(36).optional() });
const taskSchema = z.object({ onboardingCaseId: z.string().uuid(), category: z.enum(['ACCOUNT', 'EQUIPMENT', 'TRAINING', 'MENTOR', 'OTHER']), title: z.string().trim().min(1).max(255), assigneeSubject: z.string().trim().max(100).optional(), dueOn: z.string().optional() });

export function OnboardingPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const cases = useQuery({ queryKey: ['hr-onboarding-cases', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/onboarding', { params: { companyId } })).data });
  const tasks = useQuery({ queryKey: ['hr-onboarding-tasks', companyId, selected], enabled: !!selected,
    queryFn: async () => (await api.get<Row[]>(`/hr/onboarding/cases/${selected}/tasks`, { params: { companyId } })).data });
  const createCase = useMutation({ mutationFn: async (values: z.infer<typeof caseSchema>) => (await api.post<Created>('/hr/onboarding', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-onboarding-cases', companyId] }) });
  const addTask = useMutation({ mutationFn: async (values: z.infer<typeof taskSchema>) => (await api.post<Created>('/hr/onboarding/tasks', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-onboarding-tasks', companyId, selected] }) });
  const caseForm = useForm<z.infer<typeof caseSchema>>({ resolver: zodResolver(caseSchema), defaultValues: { employeeId: '', startDate: '' } });
  const taskForm = useForm<z.infer<typeof taskSchema>>({ resolver: zodResolver(taskSchema), defaultValues: { onboardingCaseId: selected || '', category: 'ACCOUNT', title: '' } });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / onboarding</span><h1>Onboarding</h1><p>Checklist nhân viên mới: tài khoản, thiết bị, đào tạo và người hướng dẫn.</p></div>
    <div className="metric-grid"><section className="panel table-panel"><h2>Hồ sơ onboarding</h2>{cases.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Ngày bắt đầu</th><th>Tiến độ</th><th>Trạng thái</th></tr></thead><tbody>{cases.data.map(row => <tr key={String(row.id)} onClick={() => setSelected(String(row.id))}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.start_date)}</td><td>{String(row.progress_percent)}%</td><td><span className="status-pill">{String(row.status)}</span></td></tr>)}</tbody></table></div> : <div className="empty-table"><ClipboardList /><p>Chưa có hồ sơ onboarding.</p></div>}</section>
    {can('hr.onboarding.write') && <section className="panel"><h2>Mở hồ sơ onboarding</h2><form className="form-grid compact-form" onSubmit={caseForm.handleSubmit(values => createCase.mutate(values))}><label>UUID nhân viên<input {...caseForm.register('employeeId')} /></label><label>Ngày bắt đầu<input type="date" {...caseForm.register('startDate')} /></label><label>UUID mentor<input {...caseForm.register('mentorEmployeeId')} /></label><div className="form-actions"><button className="primary" disabled={createCase.isPending}>Tạo checklist</button>{createCase.error && <span className="form-error">{createCase.error.message}</span>}</div></form></section>}
    </div>
    {selected && <section className="panel table-panel"><h2>Checklist · {String(cases.data?.find(row => String(row.id) === selected)?.full_name || selected)}</h2>{tasks.data?.length ? <div className="table-scroll"><table><thead><tr><th>Loại</th><th>Nội dung</th><th>Người phụ trách</th><th>Hạn</th><th>Trạng thái</th></tr></thead><tbody>{tasks.data.map(row => <tr key={String(row.id)}><td><span className="status-pill">{String(row.category)}</span></td><td>{String(row.title)}</td><td>{String(row.assignee_subject || '—')}</td><td>{String(row.due_on || '—')}</td><td><strong>{String(row.status)}</strong></td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có hạng mục.</p></div>}{can('hr.onboarding.write') && <form key={selected} className="form-grid compact-form" onSubmit={taskForm.handleSubmit(values => addTask.mutate(values))}><span className="form-section-title">Thêm hạng mục</span><label>Loại<select {...taskForm.register('category')}>{['ACCOUNT', 'EQUIPMENT', 'TRAINING', 'MENTOR', 'OTHER'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><label>Nội dung<input {...taskForm.register('title')} /></label><label>Hạn<input type="date" {...taskForm.register('dueOn')} /></label><div className="form-actions"><button className="primary" disabled={addTask.isPending}>Thêm</button>{addTask.error && <span className="form-error">{addTask.error.message}</span>}</div></form>}</section>}
  </>;
}
