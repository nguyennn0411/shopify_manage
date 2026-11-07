import { useEffect, useMemo, useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { BarChart3, Boxes, Building2, ChevronDown, ClipboardCheck, GanttChart, Handshake, LayoutDashboard, LogOut, Menu, Moon, PackageSearch, Settings, ShieldCheck, ShoppingCart, Store, Sun, Truck, UsersRound, X } from 'lucide-react';
import { useAuth } from '../features/auth/AuthProvider';

const links = [
  { to: '/', label: 'Tổng quan', icon: LayoutDashboard, permission: 'company.read' },
  { to: '/stores', label: 'Shopify Stores', icon: Store, permission: 'stores.read' },
  { to: '/orders', label: 'Sales & Orders', icon: ShoppingCart, permission: 'sales.read' },
  { to: '/inventory', label: 'Kho hàng', icon: Boxes, permission: 'inventory.read' },
  { to: '/suppliers', label: 'Nhà cung cấp', icon: Handshake, permission: 'supplier.read' },
  { to: '/procurement', label: 'Mua hàng', icon: ClipboardCheck, permission: 'procurement.read' },
  { to: '/pim', label: 'PIM sản phẩm', icon: PackageSearch, permission: 'pim.read' },
  { to: '/fulfillment', label: 'Fulfillment', icon: Truck, permission: 'fulfillment.read' },
  { to: '/hr', label: 'Nhân sự', icon: UsersRound, permission: 'hr.access' },
  { to: '/pm', label: 'Dự án & Công việc', icon: GanttChart, permission: 'pm.access' },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, permission: 'analytics.read' },
  { to: '/security', label: 'Bảo mật & Audit', icon: ShieldCheck, permission: 'audit.read' },
  { to: '/settings', label: 'Cài đặt', icon: Settings, permission: 'company.read' },
] as const;

export function AppLayout() {
  const { user, logout, can } = useAuth();
  const [menu, setMenu] = useState(false);
  const [dark, setDark] = useState(() => localStorage.getItem('erp-theme') === 'dark');
  const [selectedCompanyId, setSelectedCompanyId] = useState(() => localStorage.getItem('erp-company') || '');
  const companyId = selectedCompanyId || user?.companies[0]?.id || '';
  useEffect(() => { document.documentElement.dataset.theme = dark ? 'dark' : 'light'; localStorage.setItem('erp-theme', dark ? 'dark' : 'light'); }, [dark]);
  const visible = useMemo(() => links.filter(link => can(link.permission)), [can]);
  const company = user?.companies.find(item => item.id === companyId);
  return <div className="app-shell" data-company-id={companyId}>
    {menu && <button className="backdrop" aria-label="Đóng menu" onClick={() => setMenu(false)} />}
    <aside className={menu ? 'sidebar open' : 'sidebar'}>
      <Link className="brand" to="/"><span className="brand-mark"><Building2 size={21} /></span><span><strong>Company ERP</strong><small>Spring Operations Hub</small></span></Link>
      <div className="workspace"><small>Workspace</small><label><select value={companyId} onChange={event => { setSelectedCompanyId(event.target.value); localStorage.setItem('erp-company', event.target.value); }} aria-label="Chọn công ty"><option value="">Chọn công ty</option>{user?.companies.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select><ChevronDown size={15} /></label></div>
      <nav>{visible.map(link => { const Icon = link.icon; return <NavLink key={link.to} to={link.to} end={link.to === '/'} onClick={() => setMenu(false)}><Icon size={18} />{link.label}</NavLink>; })}</nav>
      <div className="account"><div><strong>{user?.name || user?.email}</strong><small>{user?.email}</small></div><button onClick={logout}><LogOut size={16} /> Đăng xuất</button></div>
    </aside>
    <div className="page"><header><button className="mobile-menu" onClick={() => setMenu(true)} aria-label="Mở menu"><Menu /></button><div><small>{company?.code || 'Company ERP'}</small><strong>{company?.name || 'Chưa chọn công ty'}</strong></div><button className="icon-button" onClick={() => setDark(value => !value)} aria-label="Đổi giao diện">{dark ? <Sun /> : <Moon />}</button></header><main><Outlet context={{ companyId, company }} /></main></div>
    {menu && <button className="close-menu" onClick={() => setMenu(false)}><X /></button>}
  </div>;
}
