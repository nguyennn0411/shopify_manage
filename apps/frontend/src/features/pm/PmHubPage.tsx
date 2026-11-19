import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, Briefcase, Layers } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { api } from '../../core/http/apiClient';
import type { Company, PmSummary } from '../../core/domain/types';
import { useAuth } from '../auth/AuthProvider';
import { pmModules } from './pmModules';

export function PmHubPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const summary = useQuery({ queryKey: ['pm-summary', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<PmSummary>('/pm/summary', { params: { companyId } })).data });
  const visible = pmModules.filter(module => can(module.permission)
    || ('alternatePermission' in module && can(module.alternatePermission)));
  if (!companyId) return <section className="empty-state"><span className="empty-icon"><Briefcase /></span><h1>Chọn công ty</h1><p>Chọn workspace để truy cập dữ liệu dự án và công việc.</p></section>;
  return <><div className="page-heading"><span className="eyebrow"><Layers size={15} /> Project workspace</span><h1>Quản lý công việc & dự án</h1><p>Dữ liệu trực tiếp của {company?.name}; quyền được kiểm tra lại tại backend.</p></div>
    <div className="metric-grid hr-grid">{visible.map(module => { const Icon = module.icon; const value = module.summaryKey ? summary.data?.[module.summaryKey as keyof PmSummary] : null; return <Link className="metric-card" to={`/pm/${module.key}`} key={module.key}><span className="metric-icon"><Icon /></span><ArrowUpRight className="metric-arrow" size={17} /><strong>{module.summaryKey ? (summary.isLoading ? '—' : value ?? 0) : '›'}</strong><small>{module.label}</small><p>{module.description}</p></Link>; })}</div>
    <section className="panel"><h2>Quy trình làm việc</h2><p>Công việc đi qua các trạng thái tùy chỉnh của công ty: Backlog, Đến làm, Đang thực hiện, Đang xem xét và Hoàn thành. Trạng thái được lưu theo từng dự án nên mỗi quy trình có thể có cột riêng.</p><div className="security-tags"><span>Scrum</span><span>Kanban</span><span>Backlog</span><span>Sprint</span><span>Phụ thuộc</span></div></section></>;
}
