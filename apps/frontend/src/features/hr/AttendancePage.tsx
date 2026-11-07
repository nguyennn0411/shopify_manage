import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Clock3 } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const shiftSchema = z.object({ code: z.string().trim().min(1).max(40), name: z.string().trim().min(1).max(120), startTime: z.string().min(1), endTime: z.string().min(1), breakMinutes: z.number().int().min(0).optional(), lateGraceMinutes: z.number().int().min(0).optional() });
const adjustmentSchema = z.object({ attendanceEntryId: z.string().uuid(), proposedCheckIn: z.string().optional(), proposedCheckOut: z.string().optional(), reason: z.string().trim().min(1).max(500) });
const today = () => new Date().toISOString().slice(0, 10);

export function AttendancePage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [from, setFrom] = useState(today()); const [to, setTo] = useState(today());
  const history = useQuery({ queryKey: ['hr-attendance-history', companyId, from, to], enabled: !!companyId && !!from && !!to,
    queryFn: async () => (await api.get<Row[]>('/hr/attendance/history', { params: { companyId, from, to } })).data });
  const shifts = useQuery({ queryKey: ['hr-attendance-shifts', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/attendance/shifts', { params: { companyId } })).data });
  const adjustments = useQuery({ queryKey: ['hr-attendance-adjustments', companyId], enabled: !!companyId && can('hr.attendance.approve'),
    queryFn: async () => (await api.get<Row[]>('/hr/attendance/adjustments', { params: { companyId } })).data });
  const reviewAdjustment = useMutation({ mutationFn: async (values: { requestId: string; decision: string }) =>
      (await api.post<Created>(`/hr/attendance/adjustments/${values.requestId}/review`, { decision: values.decision }, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-attendance-adjustments', companyId] }) });
  const checkIn = useMutation({ mutationFn: async () => (await api.post<Created>('/hr/attendance/check-in', null, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-attendance-history', companyId] }) });
  const checkOut = useMutation({ mutationFn: async () => (await api.post<Created>('/hr/attendance/check-out', null, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-attendance-history', companyId] }) });
  const shiftForm = useForm<z.infer<typeof shiftSchema>>({ resolver: zodResolver(shiftSchema), defaultValues: { code: '', name: '', startTime: '08:30', endTime: '17:30' } });
  const createShift = useMutation({ mutationFn: async (values: z.infer<typeof shiftSchema>) => (await api.post<Created>('/hr/attendance/shifts', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-attendance-shifts', companyId] }) });
  const adjustForm = useForm<z.infer<typeof adjustmentSchema>>({ resolver: zodResolver(adjustmentSchema), defaultValues: { attendanceEntryId: '', reason: '' } });
  const toIso = (value?: string) => (value ? new Date(value).toISOString() : undefined);
  const requestAdjustment = useMutation({ mutationFn: async (values: z.infer<typeof adjustmentSchema>) => (await api.post<Created>('/hr/attendance/adjustments', { ...values, proposedCheckIn: toIso(values.proposedCheckIn), proposedCheckOut: toIso(values.proposedCheckOut) }, { params: { companyId } })).data,
    onSuccess: () => adjustForm.reset() });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / attendance</span><h1>Chấm công</h1><p>Check-in/out theo tài khoản đang đăng nhập; đi muộn và tăng ca tính theo ca làm.</p></div>
    <section className="panel quick-action"><div><Clock3 /><div><strong>Chấm công hôm nay</strong><p>Thời gian lấy từ máy chủ; chỉnh sửa công phải qua phê duyệt.</p></div></div><div className="button-row"><button className="primary" disabled={checkIn.isPending} onClick={() => checkIn.mutate()}>Check-in</button><button className="secondary" disabled={checkOut.isPending} onClick={() => checkOut.mutate()}>Check-out</button></div>{checkIn.isSuccess && <small className="success-text">Đã ghi nhận check-in.</small>}{checkOut.isSuccess && <small className="success-text">Đã ghi nhận check-out.</small>}{checkIn.error && <small className="form-error">{checkIn.error.message}</small>}{checkOut.error && <small className="form-error">{checkOut.error.message}</small>}</section>
    {can('hr.attendance.approve') && <section className="panel table-panel"><h2>Yêu cầu chỉnh sửa chấm công</h2>{adjustments.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Giờ vào đề xuất</th><th>Giờ ra đề xuất</th><th>Lý do</th><th>Trạng thái</th><th>Duyệt</th></tr></thead><tbody>{adjustments.data.filter(row => String(row.status) === 'PENDING').map(row => <tr key={String(row.id)}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.proposed_check_in || '—')}</td><td>{String(row.proposed_check_out || '—')}</td><td>{String(row.reason)}</td><td><span className="status-pill">PENDING</span></td><td><div className="button-row"><button className="secondary" onClick={() => reviewAdjustment.mutate({ requestId: String(row.id), decision: 'APPROVED' })}>Duyệt</button><button className="secondary" onClick={() => reviewAdjustment.mutate({ requestId: String(row.id), decision: 'REJECTED' })}>Từ chối</button></div></td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Không có yêu cầu chờ duyệt.</p></div>}</section>}
    <section className="panel"><div className="quick-action-title"><CalendarClock /><div><strong>Lịch sử chấm công</strong><p>Lọc theo khoảng ngày.</p></div></div><div className="button-row"><label>Từ <input type="date" value={from} onChange={event => setFrom(event.target.value)} /></label><label>Đến <input type="date" value={to} onChange={event => setTo(event.target.value)} /></label></div>
      {history.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Ngày</th><th>Vào</th><th>Ra</th><th>Giờ làm</th><th>Muộn</th><th>Tăng ca</th><th>Trạng thái</th></tr></thead><tbody>{history.data.map(row => <tr key={String(row.id)}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.work_date)}</td><td>{String(row.check_in || '—')}</td><td>{String(row.check_out || '—')}</td><td>{String(row.worked_minutes ?? '—')} phút</td><td>{String(row.late_minutes)} phút</td><td>{String(row.overtime_minutes)} phút</td><td><span className="status-pill">{String(row.status)}</span></td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Không có bản ghi trong khoảng.</p></div>}</section>
    {can('hr.attendance.write') && <section className="panel"><div className="quick-action-title"><Clock3 /><div><strong>Nhận chỉnh sửa chấm công</strong><p>Nhân viên đề xuất giờ mới; người có quyền duyệt sẽ phê.</p></div></div>
      <form className="form-grid compact-form" onSubmit={adjustForm.handleSubmit(values => requestAdjustment.mutate(values))}><label>UUID bản ghi<input {...adjustForm.register('attendanceEntryId')} /></label><label>Giờ vào đề xuất<input type="datetime-local" {...adjustForm.register('proposedCheckIn')} /></label><label>Giờ ra đề xuất<input type="datetime-local" {...adjustForm.register('proposedCheckOut')} /></label><label className="wide-field">Lý do<textarea rows={2} {...adjustForm.register('reason')} /></label><div className="form-actions"><button className="primary" disabled={requestAdjustment.isPending}>Gửi đề xuất</button>{requestAdjustment.isSuccess && <span className="success-text">Đã gửi chờ duyệt.</span>}{requestAdjustment.error && <span className="form-error">{requestAdjustment.error.message}</span>}</div></form></section>}
    {can('hr.attendance.write') && <section className="panel"><h2>Ca làm</h2>{shifts.data?.length ? <div className="table-scroll"><table><thead><tr><th>Mã</th><th>Tên ca</th><th>Giờ</th><th>Ngỉ</th><th>Chậm được chấp nhận</th></tr></thead><tbody>{shifts.data.map(row => <tr key={String(row.id)}><td>{String(row.code)}</td><td>{String(row.name)}</td><td>{String(row.start_time)} – {String(row.end_time)}</td><td>{String(row.break_minutes)} phút</td><td>{String(row.late_grace_minutes)} phút</td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có ca làm.</p></div>}
      <form className="form-grid compact-form" onSubmit={shiftForm.handleSubmit(values => createShift.mutate(values))}><span className="form-section-title">Thêm ca làm</span><label>Mã<input {...shiftForm.register('code')} /></label><label>Tên ca<input {...shiftForm.register('name')} /></label><label>Bắt đầu<input type="time" {...shiftForm.register('startTime')} /></label><label>Kết thúc<input type="time" {...shiftForm.register('endTime')} /></label><div className="form-actions"><button className="primary" disabled={createShift.isPending}>Lưu ca</button>{createShift.error && <span className="form-error">{createShift.error.message}</span>}</div></form></section>}
  </>;
}
