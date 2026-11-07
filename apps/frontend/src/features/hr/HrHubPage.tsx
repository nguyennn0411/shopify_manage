import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, ShieldCheck, Users } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { api } from '../../core/http/apiClient';
import type { Company, HrSummary } from '../../core/domain/types';
import { useAuth } from '../auth/AuthProvider';
import { hrModules } from './hrModules';

export function HrHubPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const summary = useQuery({ queryKey: ['hr-summary', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<HrSummary>('/hr/summary', { params: { companyId } })).data });
  const visible = hrModules.filter(module => can(module.permission) || ('alternatePermission' in module && can(module.alternatePermission)));
  if (!companyId) return <section className="empty-state"><span className="empty-icon"><Users /></span><h1>Chọn công ty</h1><p>Chọn workspace để truy cập dữ liệu nhân sự.</p></section>;
  return <><div className="page-heading"><span className="eyebrow"><ShieldCheck size={15} /> Scoped HR workspace</span><h1>Quản trị nhân sự</h1><p>Dữ liệu trực tiếp của {company?.name}; quyền nhạy cảm được kiểm tra riêng tại backend.</p></div>
    <div className="metric-grid hr-grid">{visible.map(module => { const Icon = module.icon; const value = summary.data?.[module.summaryKey as keyof HrSummary]; return <Link className="metric-card" to={`/hr/${module.key}`} key={module.key}><span className="metric-icon"><Icon /></span><ArrowUpRight className="metric-arrow" size={17} /><strong>{summary.isLoading ? '—' : value ?? 0}</strong><small>{module.label}</small><p>{module.description}</p></Link>; })}</div>
    <section className="panel"><h2>Phân tách quyền dữ liệu</h2><p>Quyền hồ sơ cơ bản không bao gồm số định danh, liên hệ cá nhân hoặc tài liệu riêng tư. Quyền xem lương cá nhân không cho phép xem payroll của người khác.</p><div className="security-tags"><span>Basic profile</span><span>Sensitive profile</span><span>Own payslip</span><span>Payroll admin</span></div></section></>;
}
