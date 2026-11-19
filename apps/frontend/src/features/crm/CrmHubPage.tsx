import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, ContactRound, Layers3 } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import type { Company, CrmSummary } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { crmModules } from './crmModules';

export function CrmHubPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const summary = useQuery({ queryKey: ['crm-summary', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<CrmSummary>('/crm/summary', { params: { companyId } })).data });
  const visible = crmModules.filter(module => can(module.permission));
  if (!companyId) return <section className="empty-state"><span className="empty-icon"><ContactRound /></span><h1>Chọn công ty</h1><p>Chọn workspace để truy cập khách hàng và bán hàng.</p></section>;
  return <><div className="page-heading"><span className="eyebrow"><Layers3 size={15} /> Customer workspace</span>
    <h1>Khách hàng, kinh doanh & bán hàng</h1><p>Dữ liệu trực tiếp của {company?.name}; quyền và phạm vi được kiểm tra lại tại backend.</p></div>
    <div className="metric-grid hr-grid">{visible.map(module => { const Icon = module.icon;
      const value = module.summaryKey ? summary.data?.[module.summaryKey as keyof CrmSummary] : null;
      return <Link className="metric-card" to={`/crm/${module.key}`} key={module.key}><span className="metric-icon"><Icon /></span>
        <ArrowUpRight className="metric-arrow" size={17} /><strong>{module.summaryKey ? (summary.isLoading ? '—' : value ?? 0) : '›'}</strong>
        <small>{module.label}</small><p>{module.description}</p></Link>; })}</div>
    <section className="panel"><h2>Luồng doanh thu liên kết</h2><p>Lead → Cơ hội → Báo giá → Hợp đồng → Đơn hàng → Chăm sóc khách hàng. Mỗi bước giữ liên kết khách hàng và lịch sử kiểm toán.</p>
      <div className="security-tags"><span>Pipeline</span><span>Báo giá</span><span>Hợp đồng</span><span>SLA</span><span>Marketing ROI</span></div></section></>;
}
