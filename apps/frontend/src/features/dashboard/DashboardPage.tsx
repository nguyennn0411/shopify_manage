import { useQuery } from '@tanstack/react-query';
import { Activity, ArrowUpRight, Boxes, Handshake, PackageSearch, ShoppingCart, Store, Truck } from 'lucide-react';
import { Link, useOutletContext } from 'react-router-dom';
import { api } from '../../core/http/apiClient';
import type { Company, ModuleCounts } from '../../core/domain/types';

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
  const summary = useQuery({ queryKey: ['module-summary', companyId], enabled: !!companyId, queryFn: async () => (await api.get<ModuleCounts>('/modules/summary', { params: { companyId } })).data });
  if (!companyId) return <EmptyWorkspace />;
  return <><div className="page-heading"><span className="eyebrow"><Activity size={15} /> Live operations</span><h1>Tổng quan vận hành</h1><p>Dữ liệu trực tiếp của {company?.name}; không sử dụng bản ghi giả.</p></div><div className="metric-grid">{cards.map(card => { const Icon = card.icon; return <Link className="metric-card" to={card.path} key={card.key}><span className="metric-icon"><Icon /></span><ArrowUpRight className="metric-arrow" size={17} /><strong>{summary.isLoading ? '—' : summary.data?.[card.key] ?? 0}</strong><small>{card.label}</small></Link>; })}</div><section className="panel architecture"><div><span className="status-dot" /> Spring Boot backend</div><div>Keycloak · Redis Session · MySQL</div><div>Cloudflare WAF · Nginx</div></section></>;
}

function EmptyWorkspace() { return <section className="empty-state"><BuildingIcon /><h1>Chưa có phạm vi công ty</h1><p>Quản trị viên Keycloak cần cấp role và thêm subject của bạn vào company membership trong ERP.</p></section>; }
function BuildingIcon() { return <span className="empty-icon"><Store /></span>; }
