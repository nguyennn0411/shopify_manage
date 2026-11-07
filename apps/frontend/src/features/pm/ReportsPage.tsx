import { useQuery } from '@tanstack/react-query';
import { Receipt } from 'lucide-react';
import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { usePmProjects, type PmRow } from './usePmQueries';

type Report = {
  project: PmRow;
  progress: PmRow[];
  time: PmRow[];
  cost: PmRow[];
  staleTasks: PmRow[];
  risks: PmRow[];
  members: PmRow[];
  openIssues: PmRow[];
};

function text(row: PmRow | undefined, key: string): string {
  const v = row?.[key];
  return v === null || v === undefined ? '' : String(v);
}
function num(row: PmRow | undefined, key: string): number { const n = Number(row?.[key]); return Number.isFinite(n) ? n : 0; }

export function ReportsPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const projects = usePmProjects(companyId);
  const [projectId, setProjectId] = useState('');

  const report = useQuery<Report>({ queryKey: ['pm-report', companyId, projectId], enabled: !!companyId && !!projectId,
    queryFn: async () => (await api.get<Report>(`/pm/reports/${projectId}`, { params: { companyId } })).data });

  if (!companyId) return <section className="empty-state"><span className="empty-icon"><Receipt /></span><h1>Chọn công ty</h1><p>Chọn workspace để xem báo cáo dự án.</p></section>;

  const progress = report.data?.progress[0];
  const time = report.data?.time[0];
  const cost = report.data?.cost[0];
  const budget = num(report.data?.project, 'budget');
  const totalCost = num(cost, 'total_cost');
  const estimated = num(time, 'estimated_hours');
  const actual = num(time, 'actual_hours');
  const done = num(progress, 'done_tasks');
  const total = num(progress, 'total_tasks');
  const performance = total > 0 ? Math.round((done / total) * 100) : 0;

  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'PM'} / reports</span><h1>Báo cáo dự án</h1><p>Tiến độ, thời gian, chi phí, công việc tồn đọng, rủi ro và hiệu suất.</p></div>

    <section className="panel"><div className="button-row">
      <label>Dự án<select value={projectId} onChange={event => setProjectId(event.target.value)}>
        <option value="">Chọn dự án</option>{projects.data?.map(row => <option key={text(row, 'id')} value={text(row, 'id')}>{text(row, 'code')} · {text(row, 'name')}</option>)}</select></label>
    </div></section>

    {!projectId && <div className="empty-table"><p>Chọn một dự án để xem báo cáo.</p></div>}

    {projectId && report.isLoading && <div className="empty-table"><p>Đang tải báo cáo…</p></div>}

    {projectId && report.data && <>
      <div className="metric-grid">
        <div className="metric-card"><span className="metric-icon"><Receipt /></span><strong>{performance}%</strong><small>Hiệu suất</small><p>{done}/{total} công việc hoàn thành.</p></div>
        <div className="metric-card"><strong>{actual.toFixed(1)}</strong><small>Giờ thực tế</small><p>Dự kiến {estimated.toFixed(1)} giờ.</p></div>
        <div className="metric-card"><strong>{totalCost.toFixed(0)}</strong><small>Chi phí dự án</small><p>{budget ? `Ngân sách ${budget}` : 'Chưa đặt ngân sách'}.</p></div>
      </div>

      <section className="panel"><h2>Thời gian</h2>
        <div className="private-grid">
          <div><dt>Giờ dự kiến</dt><dd>{estimated.toFixed(1)}</dd></div>
          <div><dt>Giờ đã duyệt</dt><dd>{actual.toFixed(1)}</dd></div>
          <div><dt>Chênh lệch</dt><dd className={actual > estimated ? 'overdue-text' : ''}>{estimated ? `${(actual - estimated).toFixed(1)}` : '—'}</dd></div>
          <div><dt>Giờ đã ghi (mọi trạng thái)</dt><dd>{num(time, 'logged_hours').toFixed(1)}</dd></div>
        </div>
      </section>

      <section className="panel"><h2>Chi phí và lợi nhuận</h2>
        <div className="private-grid">
          <div><dt>Giờ đã duyệt</dt><dd>{num(cost, 'approved_hours').toFixed(1)}</dd></div>
          <div><dt>Tổng chi phí</dt><dd>{totalCost.toFixed(0)}</dd></div>
          <div><dt>Ngân sách</dt><dd>{budget || '—'}</dd></div>
          <div><dt>Lợi nhuận</dt><dd className={budget && totalCost > budget ? 'overdue-text' : ''}>{budget ? (budget - totalCost).toFixed(0) : '—'}</dd></div>
        </div>
        <p>Chi phí tính từ số giờ đã duyệt nhân với đơn giá/giờ của thành viên dự án.</p>
      </section>

      <section className="panel table-panel"><h2>Công việc tồn đọng</h2>
        {report.data.staleTasks.length ? <div className="table-scroll"><table><thead><tr>
          <th>Mã</th><th>Tiêu đề</th><th>Phụ trách</th><th>Hạn</th></tr></thead><tbody>
          {report.data.staleTasks.map(row => <tr key={text(row, 'id')}><td>{text(row, 'code')}</td><td><strong>{text(row, 'title')}</strong></td>
            <td>{text(row, 'assignee_name') || 'Chưa gán'}</td><td className="overdue-text">{text(row, 'due_date')}</td></tr>)}
        </tbody></table></div> : <div className="empty-table"><p>Không có công việc tồn đọng.</p></div>}
      </section>

      <section className="panel table-panel"><h2>Rủi ro</h2>
        {report.data.risks.length ? <div className="table-scroll"><table><thead><tr>
          <th>Rủi ro</th><th>Khả năng</th><th>Tác động</th><th>Trạng thái</th></tr></thead><tbody>
          {report.data.risks.map(row => <tr key={text(row, 'id')}><td><strong>{text(row, 'title')}</strong></td>
            <td>{text(row, 'likelihood')}</td><td>{text(row, 'impact')}</td><td><span className="status-pill">{text(row, 'status')}</span></td></tr>)}
        </tbody></table></div> : <div className="empty-table"><p>Không có rủi ro nào.</p></div>}
      </section>

      <section className="panel table-panel"><h2>Hiệu suất thành viên</h2>
        {report.data.members.length ? <div className="table-scroll"><table><thead><tr>
          <th>Thành viên</th><th>Vai trò</th><th>Công việc</th><th>Hoàn thành</th><th>Giờ duyệt</th><th>Đơn giá/giờ</th></tr></thead><tbody>
          {report.data.members.map(row => <tr key={text(row, 'employee_id')}><td><strong>{text(row, 'full_name')}</strong></td>
            <td>{text(row, 'role')}</td><td>{num(row, 'assigned_tasks')}</td><td>{num(row, 'done_tasks')}</td>
            <td>{num(row, 'approved_hours').toFixed(1)}</td><td>{text(row, 'cost_rate') || '—'}</td></tr>)}
        </tbody></table></div> : <div className="empty-table"><p>Chưa có thành viên.</p></div>}
      </section>

      <section className="panel table-panel"><h2>Sự cố đang mở</h2>
        {report.data.openIssues.length ? <div className="table-scroll"><table><thead><tr>
          <th>Mã</th><th>Tiêu đề</th><th>Loại</th><th>Mức nghiêm trọng</th></tr></thead><tbody>
          {report.data.openIssues.map(row => <tr key={text(row, 'id')}><td>{text(row, 'code')}</td><td><strong>{text(row, 'title')}</strong></td>
            <td>{text(row, 'type')}</td><td>{text(row, 'severity')}</td></tr>)}
        </tbody></table></div> : <div className="empty-table"><p>Không có sự cố đang mở.</p></div>}
      </section>
    </>}
  </>;
}
