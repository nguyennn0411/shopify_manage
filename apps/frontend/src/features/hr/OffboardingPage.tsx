import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { LogOut } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const caseSchema = z.object({ employeeId: z.string().uuid(), lastWorkingDay: z.string().min(1), resignationSubmittedOn: z.string().optional(), reasonCategory: z.string().trim().max(60).optional(), reason: z.string().trim().max(2000).optional() });

export function OffboardingPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [selected, setSelected] = useState<string | null>(null);
  const cases = useQuery({ queryKey: ['hr-offboarding-cases', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/offboarding', { params: { companyId } })).data });
  const tasks = useQuery({ queryKey: ['hr-offboarding-tasks', companyId, selected], enabled: !!selected,
    queryFn: async () => (await api.get<Row[]>(`/hr/offboarding/cases/${selected}/tasks`, { params: { companyId } })).data });
  const form = useForm<z.infer<typeof caseSchema>>({ resolver: zodResolver(caseSchema), defaultValues: { employeeId: '', lastWorkingDay: '' } });
  const createCase = useMutation({ mutationFn: async (values: z.infer<typeof caseSchema>) => (await api.post<Created>('/hr/offboarding', values, { params: { companyId } })).data,
    onSuccess: () => { form.reset(); queryClient.invalidateQueries({ queryKey: ['hr-offboarding-cases', companyId] }); } });
  const completeTask = useMutation({ mutationFn: async (values: { taskId: string; decision: string }) =>
      (await api.post<Created>(`/hr/offboarding/tasks/${values.taskId}/decision`, { decision: values.decision }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-offboarding-tasks', companyId, selected] }) });
  const decideCase = useMutation({ mutationFn: async (values: { caseId: string; status: string }) =>
      (await api.post<Created>(`/hr/offboarding/${values.caseId}/decision`, { status: values.status }, { params: { companyId } })).data,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['hr-offboarding-cases', companyId] }); } });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / offboarding</span><h1>Offboarding</h1><p>Đơn nghỉ việc, bàn giao, thu hồi tài sản/tài khoản, hoàn tất nghĩa vụ và lưu trữ hồ sơ.</p></div>
    <section className="panel table-panel"><h2>Vụ nghỉ việc</h2>{cases.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Nghỉ việc từ</th><th>Ngày cuối</th><th>Lý do</th><th>Trạng thái</th>{can('hr.offboarding.write') && <th>Hoàn tất</th>}</tr></thead><tbody>{cases.data.map(row => <tr key={String(row.id)} onClick={() => setSelected(String(row.id))}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.resignation_submitted_on || '—')}</td><td>{String(row.last_working_day)}</td><td>{String(row.reason_category || '—')}</td><td><span className="status-pill">{String(row.status)}</span></td>{can('hr.offboarding.write') && String(row.status) !== 'COMPLETED' ? <td><button className="secondary" onClick={event => { event.stopPropagation(); decideCase.mutate({ caseId: String(row.id), status: 'COMPLETED' }); }}>Hoàn tất</button></td> : null}</tr>)}</tbody></table></div> : <div className="empty-table"><LogOut /><p>Chưa có vụ nghỉ việc.</p></div>}</section>
    {can('hr.offboarding.write') && <form className="panel form-grid compact-form" onSubmit={form.handleSubmit(values => createCase.mutate(values))}><span className="form-section-title">Ghi nhận đơn nghỉ việc</span><label>UUID nhân viên<input {...form.register('employeeId')} /></label><label>Ngày làm việc cuối<input type="date" {...form.register('lastWorkingDay')} /></label><label>Ngày nộp đơn<input type="date" {...form.register('resignationSubmittedOn')} /></label><label>Ngành lý do<input {...form.register('reasonCategory')} /></label><label className="wide-field">Lý do (mã hóa)<textarea rows={2} {...form.register('reason')} /></label><div className="form-actions"><button className="primary" disabled={createCase.isPending}>Tạo hồ sơ offboarding</button>{createCase.error && <span className="form-error">{createCase.error.message}</span>}</div></form>}
    {selected && <section className="panel table-panel"><h2>Bàn giao · {String(cases.data?.find(row => String(row.id) === selected)?.full_name || selected)}</h2>{tasks.data?.length ? <div className="table-scroll"><table><thead><tr><th>Loại</th><th>Nội dung</th><th>Tài sản</th><th>Tài khoản</th><th>Trạng thái</th>{can('hr.offboarding.write') && <th>Xong</th>}</tr></thead><tbody>{tasks.data.map(row => <tr key={String(row.id)}><td><span className="status-pill">{String(row.category)}</span></td><td>{String(row.title)}</td><td>{String(row.asset_reference || '—')}</td><td>{String(row.account_reference || '—')}</td><td><strong>{String(row.status)}</strong></td>{can('hr.offboarding.write') && String(row.status) !== 'COMPLETED' ? <td><button className="secondary" onClick={() => completeTask.mutate({ taskId: String(row.id), decision: 'COMPLETE' })}>Đánh dấu xong</button></td> : null}</tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có hạng mục bàn giao.</p></div>}</section>}
  </>;
}
