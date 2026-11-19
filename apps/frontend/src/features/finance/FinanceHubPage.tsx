import { useQuery } from '@tanstack/react-query';
import { ArrowUpRight, BadgeDollarSign, ShieldCheck } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import type { Company, FinanceSummary } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';
import { financeModules } from './financeModules';

const currency=(value:unknown,code:string)=>`${Number(value||0).toLocaleString('vi-VN')} ${code}`;

export function FinanceHubPage(){
  const {companyId,company}=useOutletContext<{companyId:string;company?:Company}>();const {can}=useAuth();
  const summary=useQuery({queryKey:['finance-summary',companyId],enabled:!!companyId,queryFn:async()=>(await api.get<FinanceSummary>('/finance/summary',{params:{companyId}})).data});
  const visible=financeModules.filter(module=>can(module.permission));
  if(!companyId)return <section className="empty-state"><span className="empty-icon"><BadgeDollarSign/></span><h1>Chọn công ty</h1><p>Chọn workspace để truy cập tài chính quản trị.</p></section>;
  return <><div className="page-heading"><span className="eyebrow"><ShieldCheck size={15}/> Financial control</span><h1>Tài chính & Kế toán quản trị</h1><p>Thu chi, ngân sách, công nợ và sổ quản trị của {company?.name}; mọi số liệu đều theo quyền và phạm vi được cấp.</p></div>
    <div className="metric-grid hr-grid">{visible.map(module=>{const Icon=module.icon;const raw=module.summaryKey?summary.data?.[module.summaryKey as keyof FinanceSummary]:null;const monetary=['availableBudget','profit'].includes(module.summaryKey||'');return <Link className="metric-card" to={`/finance/${module.key}`} key={module.key}><span className="metric-icon"><Icon/></span><ArrowUpRight className="metric-arrow" size={17}/><strong>{module.summaryKey?(summary.isLoading?'—':monetary?currency(raw,company?.baseCurrency||'VND'):raw??0):'›'}</strong><small>{module.label}</small><p>{module.description}</p></Link>})}</div>
    <section className="panel"><h2>Kiểm soát tài chính liên kết</h2><p>Đề nghị → Duyệt → Kiểm tra ngân sách → Ghi nhận thanh toán → Đối soát → Báo cáo. Giai đoạn này phục vụ quản trị nội bộ, không thay thế khai thuế hoặc hóa đơn điện tử.</p><div className="security-tags"><span>Phân tách người lập/người duyệt</span><span>Hạn mức ngân sách</span><span>Công nợ đến hạn</span><span>Bút toán cân đối</span><span>Khóa kỳ</span></div></section></>;
}
