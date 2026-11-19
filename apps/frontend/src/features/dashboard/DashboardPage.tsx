import { useQuery } from '@tanstack/react-query';
import { Activity, AlertTriangle, ArrowUpRight, Boxes, CircleDollarSign, FolderKanban, Handshake, PackageSearch, ShoppingCart, Store, Target, Truck, UsersRound } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { api } from '../../core/http/apiClient';
import type { Company, ExecutiveSummary, ModuleCounts } from '../../core/domain/types';
import { useAuth } from '../auth/AuthProvider';

const cards = [
  { key: 'stores', label: 'Shopify Stores', path: '/stores', icon: Store },
  { key: 'orders', label: 'Đơn hàng', path: '/orders', icon: ShoppingCart },
  { key: 'products', label: 'Sản phẩm', path: '/pim', icon: PackageSearch },
  { key: 'suppliers', label: 'Nhà cung cấp', path: '/suppliers', icon: Handshake },
  { key: 'warehouses', label: 'Kho hàng', path: '/inventory', icon: Boxes },
  { key: 'fulfillment', label: 'Fulfillment', path: '/fulfillment', icon: Truck },
] as const;

export function DashboardPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const executive = can('reporting.read');
  const summary = useQuery({ queryKey: ['module-summary', companyId], enabled: !!companyId&&!executive, queryFn: async () => (await api.get<ModuleCounts>('/modules/summary', { params: { companyId } })).data });
  const report = useQuery({ queryKey: ['reporting-summary', companyId], enabled: !!companyId&&executive, queryFn: async () => (await api.get<ExecutiveSummary>('/reporting/summary', { params: { companyId } })).data });
  if (!companyId) return <EmptyWorkspace />;
  if(executive){const data=report.data;const money=(value:number|string|undefined)=>new Intl.NumberFormat('vi-VN',{style:'currency',currency:data?.currency||'VND',maximumFractionDigits:0}).format(Number(value||0));const metrics=[{label:'Nhân sự hoạt động',value:data?.activeEmployees??0,icon:UsersRound},{label:'Dự án đang chạy',value:data?.activeProjects??0,icon:FolderKanban},{label:'Công việc quá hạn',value:data?.overdueTasks??0,icon:AlertTriangle},{label:'Lợi nhuận từ đầu năm',value:money(data?.profit),icon:CircleDollarSign},{label:'Chờ phê duyệt',value:data?.pendingApprovals??0,icon:Activity},{label:'Mục tiêu rủi ro',value:data?.objectivesAtRisk??0,icon:Target}];return <><div className="page-heading"><span className="eyebrow"><Activity size={15}/> Executive overview</span><h1>Tổng quan điều hành</h1><p>KPI trực tiếp theo phạm vi được cấp tại {company?.name}; số tiền dùng {data?.currency||'đồng tiền cơ sở'}.</p></div><div className="metric-grid">{metrics.map(item=>{const Icon=item.icon;return <Link className="metric-card" to="/reporting" key={item.label}><span className="metric-icon"><Icon/></span><ArrowUpRight className="metric-arrow" size={17}/><strong>{report.isLoading?'—':item.value}</strong><small>{item.label}</small></Link>})}</div><section className="panel architecture"><div><span className="status-dot"/> Dữ liệu trực tiếp theo quyền</div><div>HR · Dự án · CRM · Tài chính · Vận hành</div><div><Link to="/reporting">Mở trung tâm báo cáo</Link></div></section></>}
  return <><div className="page-heading"><span className="eyebrow"><Activity size={15} /> Live operations</span><h1>Tổng quan vận hành</h1><p>Dữ liệu trực tiếp của {company?.name}; không sử dụng bản ghi giả.</p></div><div className="metric-grid">{cards.map(card => { const Icon = card.icon; return <Link className="metric-card" to={card.path} key={card.key}><span className="metric-icon"><Icon /></span><ArrowUpRight className="metric-arrow" size={17} /><strong>{summary.isLoading ? '—' : summary.data?.[card.key] ?? 0}</strong><small>{card.label}</small></Link>; })}</div><section className="panel architecture"><div><span className="status-dot" /> Spring Boot backend</div><div>Keycloak · Redis Session · PostgreSQL</div><div>Cloudflare WAF · Nginx</div></section></>;
}

function EmptyWorkspace() { return <section className="empty-state"><BuildingIcon /><h1>Chưa có phạm vi công ty</h1><p>Quản trị viên Keycloak cần cấp role và thêm subject của bạn vào company membership trong ERP.</p></section>; }
function BuildingIcon() { return <span className="empty-icon"><Store /></span>; }
