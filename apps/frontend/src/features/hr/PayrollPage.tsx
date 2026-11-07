import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { LockKeyhole, ReceiptText } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company, Employee, Payslip } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const runSchema = z.object({ periodStart: z.string().min(1), periodEnd: z.string().min(1), currency: z.string().trim().length(3).toUpperCase() });
const structureSchema = z.object({ employeeId: z.string().uuid(), effectiveFrom: z.string().min(1), effectiveTo: z.string().optional(), currency: z.string().trim().length(3).toUpperCase(), baseSalary: z.number().min(0), allowances: z.number().min(0).optional(), deductions: z.number().min(0).optional() });

export function PayrollPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [runId, setRunId] = useState<string | null>(null);
  const runs = useQuery({ queryKey: ['hr-payroll-runs', companyId], enabled: !!companyId && can('hr.payroll.read.all'),
    queryFn: async () => (await api.get<Row[]>('/hr/payroll/runs', { params: { companyId } })).data });
  const ownEmployee = useQuery({ queryKey: ['hr-own-employee', companyId], enabled: !!companyId && can('hr.payroll.read.own') && !can('hr.payroll.read.all'),
    queryFn: async () => (await api.get<Employee>('/hr/employees/me', { params: { companyId } })).data });
  const payslips = useQuery({ queryKey: ['hr-own-payslips', companyId, ownEmployee.data?.id], enabled: !!ownEmployee.data,
    queryFn: async () => (await api.get<Payslip[]>('/hr/payroll/payslips', { params: { companyId, employeeId: ownEmployee.data!.id } })).data });
  const history = useQuery({ queryKey: ['hr-payroll-history', companyId, runId], enabled: !!runId && can('hr.payroll.read.all'), retry: false,
    queryFn: async () => (await api.get<Row[]>(`/hr/payroll/runs/${runId}/history`, { params: { companyId } })).data });
  const runForm = useForm<z.infer<typeof runSchema>>({ resolver: zodResolver(runSchema), defaultValues: { currency: 'VND' } });
  const structureForm = useForm<z.infer<typeof structureSchema>>({ resolver: zodResolver(structureSchema), defaultValues: { employeeId: '', effectiveFrom: '', currency: 'VND', baseSalary: 0 } });
  const createRun = useMutation({ mutationFn: async (values: z.infer<typeof runSchema>) => (await api.post<Created>('/hr/payroll/runs', values, { params: { companyId } })).data,
    onSuccess: () => { runForm.reset(); setRunId(null); queryClient.invalidateQueries({ queryKey: ['hr-payroll-runs', companyId] }); } });
  const createStructure = useMutation({ mutationFn: async (values: z.infer<typeof structureSchema>) => (await api.post<Created>('/hr/payroll/salary-structures', values, { params: { companyId } })).data,
    onSuccess: () => structureForm.reset() });
  const decide = useMutation({ mutationFn: async (values: { id: string; decision: string }) =>
      (await api.post<Created>(`/hr/payroll/runs/${values.id}/decision`, { runId: values.id, decision: values.decision }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-payroll-runs', companyId] }) });
  const publish = useMutation({ mutationFn: async (values: { id: string; currency: string }) => (await api.post<Row[]>(`/hr/payroll/runs/${values.id}/publish-payslips`, { runId: values.id, currency: values.currency }, { params: { companyId } })).data,
    onSuccess: () => { setRunId(null); queryClient.invalidateQueries({ queryKey: ['hr-payroll-runs', companyId] }); } });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / payroll</span><h1>Lương & phúc lợi</h1><p>Cấu trúc lương, bảng lương, phiếu lương, lịch sử điều chỉnh và quyền xem lương được tách riêng.</p></div>
    <section className="panel"><div className="security-banner"><LockKeyhole /><div><strong>Dữ liệu lương được mã hóa</strong><p>Trưởng phòng không mặc định xem được lương. Chỉ <code>hr.payroll.read.all</code> xem bảng lương toàn công ty; nhân viên dùng <code>hr.payroll.read.own</code> cho phiếu lương của chính mình.</p></div></div></section>
    {can('hr.payroll.read.all') && <section className="panel table-panel"><h2>Kỳ lương</h2>{runs.data?.length ? <div className="table-scroll"><table><thead><tr><th>Kỳ</th><th>Tiền tệ</th><th>Trạng thái</th><th>Duyệt lúc</th>{can('hr.payroll.write') && <th>Thao tác</th>}</tr></thead><tbody>{runs.data.map(row => <tr key={String(row.id)}><td>{String(row.period_start)} → {String(row.period_end)}</td><td>{String(row.currency)}</td><td><span className="status-pill">{String(row.status)}</span></td><td>{String(row.approved_at || '—')}</td>{can('hr.payroll.write') && <td><div className="button-row">{String(row.status) === 'DRAFT' && <button className="secondary" onClick={() => decide.mutate({ id: String(row.id), decision: 'APPROVED' })}>Duyệt</button>}{String(row.status) === 'APPROVED' && <><button className="secondary" onClick={() => setRunId(String(row.id))}>Xem lịch sử</button><button className="primary" onClick={() => publish.mutate({ id: String(row.id), currency: String(row.currency) })}>Xuất phiếu lương</button></>}{String(row.status) === 'PAID' && <button className="secondary" onClick={() => decide.mutate({ id: String(row.id), decision: 'PAID' })}>Đã trả</button>}</div></td>}</tr>)}</tbody></table></div> : <div className="empty-table"><ReceiptText /><p>Chưa có kỳ lương.</p></div>}
      {history.data?.length != null && <div className="table-scroll"><table><thead><tr><th>Thời điểm</th><th>Thao tác</th><th>Người thực hiện</th></tr></thead><tbody>{history.data.map((row, index) => <tr key={index}><td>{String(row.created_at)}</td><td>{String(row.action)}</td><td>{String(row.actor_subject)}</td></tr>)}</tbody></table></div>}</section>}
    {can('hr.payroll.write') && <form className="panel form-grid compact-form" onSubmit={runForm.handleSubmit(values => createRun.mutate(values))}><span className="form-section-title">Tạo kỳ lương</span><label>Từ<input type="date" {...runForm.register('periodStart')} /></label><label>Đến<input type="date" {...runForm.register('periodEnd')} /></label><label>Tiền tệ (3 ký tự)<input {...runForm.register('currency')} /></label><div className="form-actions"><button className="primary" disabled={createRun.isPending}>Tạo kỳ</button>{createRun.error && <span className="form-error">{createRun.error.message}</span>}</div></form>}
    {can('hr.payroll.write') && <form className="panel form-grid compact-form" onSubmit={structureForm.handleSubmit(values => createStructure.mutate(values))}><span className="form-section-title">Cấu trúc lương (mã hóa)</span><label>UUID nhân viên<input {...structureForm.register('employeeId')} /></label><label>Hiệu lực từ<input type="date" {...structureForm.register('effectiveFrom')} /></label><label>Tiền tệ<input {...structureForm.register('currency')} /></label><label>Lương cơ bản<input type="number" min={0} {...structureForm.register('baseSalary', { valueAsNumber: true })} /></label><label>Phụ cấp<input type="number" min={0} {...structureForm.register('allowances', { valueAsNumber: true })} /></label><label>Khấu trừ<input type="number" min={0} {...structureForm.register('deductions', { valueAsNumber: true })} /></label><div className="form-actions"><button className="primary" disabled={createStructure.isPending}>Lưu cấu trúc</button>{createStructure.error && <span className="form-error">{createStructure.error.message}</span>}</div></form>}
    {publish.isSuccess && <section className="panel"><h2>Phiếu lương vừa xuất</h2>{publish.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Gross</th><th>Net</th></tr></thead><tbody>{publish.data.map(row => <tr key={String(row.employeeId)}><td>{String(row.employeeId)}</td><td>{String(row.gross)}</td><td>{String(row.net)}</td></tr>)}</tbody></table></div> : <p>Không có cấu trúc lương nào áp dụng.</p>}</section>}
    {!can('hr.payroll.read.all') && can('hr.payroll.read.own') && <section className="panel table-panel"><h2>Phiếu lương của tôi</h2>{payslips.data?.length ? <div className="table-scroll"><table><thead><tr><th>Kỳ lương</th><th>Gross</th><th>Khấu trừ</th><th>Net</th><th>Tiền tệ</th></tr></thead><tbody>{payslips.data.map(slip => <tr key={slip.id}><td>{slip.periodStart} – {slip.periodEnd}</td><td>{slip.gross}</td><td>{slip.deductions}</td><td><strong>{slip.net}</strong></td><td>{slip.currency}</td></tr>)}</tbody></table></div> : <div className="empty-table"><p>{ownEmployee.isError ? 'Tài khoản chưa được liên kết với hồ sơ nhân viên.' : 'Chưa có phiếu lương.'}</p></div>}</section>}
  </>;
}
