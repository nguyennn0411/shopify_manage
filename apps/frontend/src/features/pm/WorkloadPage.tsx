import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ClipboardList, Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { usePmEmployees, type PmRow } from './usePmQueries';

type Created = { id: string; status: string };

const capacitySchema = z.object({
  employeeId: z.string().min(1, 'Chọn một nhân viên'),
  weeklyCapacityHours: z.number().min(0).max(240),
});
type CapacityForm = z.infer<typeof capacitySchema>;

function text(row: PmRow, key: string): string { const v = row[key]; return v === null || v === undefined ? '' : String(v); }
function num(row: PmRow, key: string): number { const n = Number(row[key]); return Number.isFinite(n) ? n : 0; }

export function WorkloadPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const [range, setRange] = useState({ from: '', to: '' });
  const employees = usePmEmployees(companyId);

  const rows = useQuery({ queryKey: ['pm-workload', companyId, range], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/workload', {
      params: { companyId, from: range.from || undefined, to: range.to || undefined } })).data });
  const conflicts = useQuery({ queryKey: ['pm-workload-conflicts', companyId, range], enabled: !!companyId,
    queryFn: async () => (await api.get<PmRow[]>('/pm/workload/conflicts', {
      params: { companyId, from: range.from || undefined, to: range.to || undefined } })).data });

  const setCapacity = useMutation({ mutationFn: async (values: CapacityForm) =>
      (await api.post<Created>('/pm/workload/capacity', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pm-workload', companyId] }) });
  const form = useForm<CapacityForm>({ resolver: zodResolver(capacitySchema),
    defaultValues: { employeeId: '', weeklyCapacityHours: 40 } });

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><ClipboardList /></span><h1>Chọn công ty</h1><p>Chọn workspace để xem khối lượng công việc.</p></section>;

  const teamCapacity = (rows.data ?? []).reduce((sum, row) => sum + num(row, 'capacity_hours'), 0);

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / workload</span><h1>Khối lượng công việc</h1><p>Tải lượng từng người, lịch phân công và xung đột thời gian.</p></div>

    <section className="panel"><div className="button-row">
      <label>Từ<input type="date" value={range.from} onChange={event => setRange(value => ({ ...value, from: event.target.value }))} /></label>
      <label>Đến<input type="date" value={range.to} onChange={event => setRange(value => ({ ...value, to: event.target.value }))} /></label>
      <span className="balance-row"><span>Năng lực đội: <strong>{teamCapacity ? teamCapacity.toFixed(0) : '—'} giờ/tuần</strong></span></span>
    </div></section>

    <section className="panel table-panel"><h2>Tải lượng theo người</h2>
      {rows.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Nhân viên</th><th>Phòng ban</th><th>Năng lực</th><th>Yêu cầu</th><th>Đã ghi</th><th>Công việc mở</th><th>Quá hạn</th><th>Tải</th></tr></thead><tbody>
        {rows.data.map(row => {
          const capacity = num(row, 'capacity_hours');
          const planned = num(row, 'planned_hours');
          const ratio = capacity > 0 ? Math.min((planned / capacity) * 100, 100) : 0;
          const over = capacity > 0 && planned > capacity;
          return <tr key={text(row, 'employee_id')}>
            <td><strong>{text(row, 'full_name')}</strong><small>{text(row, 'employee_code')}</small></td>
            <td>{text(row, 'department_name') || '—'}</td>
            <td>{capacity ? capacity.toFixed(0) : '—'}</td>
            <td>{planned.toFixed(1)}</td>
            <td>{num(row, 'actual_hours').toFixed(1)}</td>
            <td>{num(row, 'open_tasks')}</td>
            <td className={num(row, 'overdue_tasks') > 0 ? 'overdue-text' : ''}>{num(row, 'overdue_tasks')}</td>
            <td><div className="load-bar"><div className={`load-fill ${over ? 'over' : ''}`} style={{ width: `${ratio}%` }} /></div></td>
          </tr>;
        })}
      </tbody></table></div> : <div className="empty-table"><p>Chưa có dữ liệu tải lượng.</p></div>}
    </section>

    {can('pm.workload.read') && <section className="panel"><div className="quick-action-title"><Plus />
      <div><strong>Chỉnh năng lực</strong><p>Giờ làm việc tối đa mỗi tuần của một người.</p></div></div>
      <form className="form-grid compact-form" onSubmit={form.handleSubmit(values => setCapacity.mutate(values))}>
        <label>Nhân viên<select {...form.register('employeeId')}><option value="">Chọn nhân viên</option>{employees.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'fullName')}</option>)}</select></label>
        <label>Năng lực (giờ/tuần)<input type="number" min="0" max="240" step="0.5" {...form.register('weeklyCapacityHours', { valueAsNumber: true })} /></label>
        <div className="form-actions"><button className="secondary" disabled={setCapacity.isPending}>Lưu</button>
          {setCapacity.isSuccess && <span className="success-text">Đã lưu năng lực.</span>}{setCapacity.error && <span className="form-error">{setCapacity.error.message}</span>}</div>
      </form>
    </section>}

    <section className="panel"><h2>Xung đột thời gian</h2>
      <p>Công việc mở trùng hạn chót hoặc dính đợt nghỉ đã duyệt.</p>
      {conflicts.data?.length ? <div className="table-scroll"><table><thead><tr>
        <th>Công việc</th><th>Phụ trách</th><th>Hạn</th><th>Ước lượng</th><th>Chồng nghỉ</th></tr></thead><tbody>
        {conflicts.data.map(row => <tr key={text(row, 'id')}>
          <td><strong>{text(row, 'code')}</strong> {text(row, 'title')}</td>
          <td>{text(row, 'assignee_name') || 'Chưa gán'}</td>
          <td className={text(row, 'leave_from') ? 'overdue-text' : ''}>{text(row, 'due_date') || '—'}</td>
          <td>{text(row, 'estimated_hours') || '—'}</td>
          <td>{text(row, 'leave_from') ? `${text(row, 'leave_from')} → ${text(row, 'leave_to')}` : '—'}</td></tr>)}
      </tbody></table></div> : <div className="empty-table"><p>Không có xung đột trong khoảng đã chọn.</p></div>}
    </section>
  </>;
}
