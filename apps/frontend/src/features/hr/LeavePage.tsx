import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Palmtree } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const leaveSchema = z.object({ leaveTypeId: z.string().uuid(), startsOn: z.string().min(1), endsOn: z.string().min(1),
  requestedDays: z.number().min(0.5), reason: z.string().trim().max(500).optional() })
  .refine(value => value.endsOn >= value.startsOn, { path: ['endsOn'], message: 'Ngày kết thúc không hợp lệ' });
type LeaveForm = z.infer<typeof leaveSchema>;

export function LeavePage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [range, setRange] = useState({ from: '', to: '' });
  const requests = useQuery({ queryKey: ['hr-leave-requests', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/leave', { params: { companyId } })).data });
  const types = useQuery({ queryKey: ['hr-leave-types', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/leave/types', { params: { companyId } })).data });
  const balances = useQuery({ queryKey: ['hr-leave-balances', companyId], enabled: !!companyId, retry: false,
    queryFn: async () => (await api.get<Row[]>('/hr/leave/balances/me', { params: { companyId } })).data });
  const calendar = useQuery({ queryKey: ['hr-leave-calendar', companyId, range], enabled: !!companyId && !!range.from && !!range.to, retry: false,
    queryFn: async () => (await api.get<Row[]>('/hr/leave/calendar', { params: { companyId, from: range.from, to: range.to } })).data });
  const form = useForm<LeaveForm>({ resolver: zodResolver(leaveSchema), defaultValues: { leaveTypeId: '', startsOn: '', endsOn: '', requestedDays: 1, reason: '' } });
  const submit = useMutation({ mutationFn: async (values: LeaveForm) => (await api.post<Created>('/hr/leave', values, { params: { companyId } })).data,
    onSuccess: async () => { form.reset(); await Promise.all([queryClient.invalidateQueries({ queryKey: ['hr-leave-requests', companyId] }), queryClient.invalidateQueries({ queryKey: ['hr-leave-balances', companyId] })]); } });
  const review = useMutation({ mutationFn: async (values: { requestId: string; decision: string; note?: string }) =>
      (await api.post<Created>(`/hr/leave/${values.requestId}/review`, { decision: values.decision, note: values.note }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-leave-requests', companyId] }) });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / leave</span><h1>Nghỉ phép</h1><p>Phép năm, nghỉ ốm, nghỉ không lương; số dư trừ sau khi duyệt.</p></div>
    {can('hr.leave.write') && <section className="panel"><div className="quick-action-title"><Palmtree /><div><strong>Đăng ký nghỉ phép</strong><p>Đơn đi vào luồng phê duyệt; số dư chỉ trừ khi được duyệt.</p></div></div>
      {balances.data?.length ? <div className="balance-row">{balances.data.map(balance => <span key={String(balance.id)}>{String(balance.name)} · năm {String(balance.balance_year)}: còn <strong>{String(balance.remaining_days)}</strong> ngày</span>)}</div> : null}
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => submit.mutate(values))}><label>Loại nghỉ<select {...form.register('leaveTypeId')}><option value="">Chọn loại nghỉ</option>{types.data?.map(type => <option key={String(type.id)} value={String(type.id)}>{String(type.name)}</option>)}</select></label><label>Từ ngày<input type="date" {...form.register('startsOn')} /></label><label>Đến ngày<input type="date" {...form.register('endsOn')} /></label><label>Số ngày<input type="number" min="0.5" step="0.5" {...form.register('requestedDays', { valueAsNumber: true })} /></label><label className="wide-field">Lý do<textarea rows={2} {...form.register('reason')} /></label><div className="form-actions"><button className="primary" disabled={submit.isPending || !types.data?.length}>Gửi đơn</button>{submit.isSuccess && <span className="success-text">Đã gửi đơn chờ duyệt.</span>}{submit.error && <span className="form-error">{submit.error.message}</span>}</div></form></section>}
    <section className="panel table-panel"><h2>Đơn nghỉ phép</h2>{requests.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Loại</th><th>Thời gian</th><th>Số ngày</th><th>Trạng thái</th>{can('hr.leave.approve') && <th>Duyệt</th>}</tr></thead><tbody>{requests.data.map(row => <tr key={String(row.id)}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.leave_type)}</td><td>{String(row.starts_on)} → {String(row.ends_on)}</td><td>{String(row.requested_days)}</td><td><span className="status-pill">{String(row.status)}</span></td>{can('hr.leave.approve') && String(row.status) === 'PENDING' ? <td><div className="button-row"><button className="secondary" onClick={() => review.mutate({ requestId: String(row.id), decision: 'APPROVED' })}>Duyệt</button><button className="secondary" onClick={() => review.mutate({ requestId: String(row.id), decision: 'REJECTED' })}>Từ chối</button></div></td> : null}</tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có đơn nghỉ.</p></div>}</section>
    <section className="panel"><div className="quick-action-title"><Palmtree /><div><strong>Lịch nghỉ toàn công ty</strong><p>Chỉ hiện đơn đã được duyệt.</p></div></div><div className="button-row"><label>Từ <input type="date" value={range.from} onChange={event => setRange(value => ({ ...value, from: event.target.value }))} /></label><label>Đến <input type="date" value={range.to} onChange={event => setRange(value => ({ ...value, to: event.target.value }))} /></label></div>
      {calendar.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Loại</th><th>Thời gian</th></tr></thead><tbody>{calendar.data.map(row => <tr key={String(row.id)}><td>{String(row.full_name)} · {String(row.employee_code)}</td><td>{String(row.leave_type)}</td><td>{String(row.starts_on)} → {String(row.ends_on)}</td></tr>)}</tbody></table></div> : <p className="table-message">Chọn khoảng ngày để xem lịch nghỉ.</p>}</section>
  </>;
}
