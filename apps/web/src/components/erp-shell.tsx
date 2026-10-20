'use client';
import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from 'next/link';
import { Activity, BadgeDollarSign, BarChart3, Barcode, Boxes, Building2, ChevronDown, CircleUserRound, ClipboardCheck, ClipboardList, ContactRound, Handshake, LayoutDashboard, Layers3, LogOut, Menu, Moon, Package, PackageSearch, PanelsTopLeft, Shield, ShoppingCart, Store, Sun, Truck, UserRoundCog, Users, X } from 'lucide-react';
import { api, Me } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { AdminView } from './erp-view';
import { CommerceView } from './commerce-view';
import { FinanceView } from './finance-view';
import { OperationsView } from './operations-view';
import { PeopleView } from './people-view';
import { AnalyticsView } from './analytics-view';
import { LifecycleView } from './lifecycle-view';

const links = [
  { href: '/dashboard', label: 'Tổng quan', icon: LayoutDashboard, permission: 'company.read' },
  { href: '/dashboard/stores', label: 'Shopify Stores', icon: Store, permission: 'stores.read' },
  { href: '/dashboard/orders', label: 'Đơn hàng', icon: ShoppingCart, permission: 'sales.read', scope: 'store' },
  { href: '/dashboard/products', label: 'Sản phẩm', icon: Package, permission: 'sales.read', scope: 'store' },
  { href: '/dashboard/inventory', label: 'Tồn kho', icon: Boxes, permission: 'inventory.read', scope: 'store' },
  { href: '/dashboard/customers', label: 'Khách hàng', icon: ContactRound, permission: 'sales.read', scope: 'store' },
  { href: '/dashboard/skus', label: 'SKU nội bộ', icon: Barcode, permission: 'inventory.read', scope: 'store' },
  { href: '/dashboard/finance', label: 'Tài chính & lợi nhuận', icon: BadgeDollarSign, permission: 'finance.read' },
  { href: '/dashboard/suppliers', label: 'Nhà cung cấp', icon: Handshake, permission: 'supplier.read' },
  { href: '/dashboard/procurement', label: 'Mua hàng', icon: ClipboardCheck, permission: 'procurement.read', scope: 'warehouse' },
  { href: '/dashboard/pim', label: 'PIM sản phẩm', icon: PackageSearch, permission: 'pim.read' },
  { href: '/dashboard/fulfillment', label: 'Fulfillment', icon: Truck, permission: 'fulfillment.read', scope: 'warehouse' },
  { href: '/dashboard/operations', label: 'Vận hành nâng cao', icon: PanelsTopLeft, permission: 'inventory.read', scope: 'warehouse' },
  { href: '/dashboard/people', label: 'Nhân sự & công ty', icon: UserRoundCog, permission: 'hr.read' },
  { href: '/dashboard/analytics', label: 'BI & tự động hóa', icon: BarChart3, permission: 'analytics.read' },
  { href: '/dashboard/users', label: 'Người dùng', icon: Users, permission: 'users.read' },
  { href: '/dashboard/roles', label: 'Vai trò & quyền', icon: Shield, permission: 'roles.read' },
  { href: '/dashboard/audit', label: 'Audit Logs', icon: ClipboardList, permission: 'audit.read' },
  { href: '/dashboard/settings', label: 'Cài đặt công ty', icon: Building2, permission: 'company.read' },
] as const;

export type Section = 'overview' | 'stores' | 'orders' | 'products' | 'inventory' | 'customers' | 'skus' | 'finance' | 'suppliers' | 'procurement' | 'pim' | 'fulfillment' | 'operations' | 'people' | 'analytics' | 'users' | 'roles' | 'audit' | 'settings';
const commerceSections: Section[] = ['orders', 'products', 'inventory', 'customers', 'skus'];
const lifecycleSections: Section[] = ['suppliers', 'procurement', 'pim', 'fulfillment'];
const title: Record<Section, string> = { overview: 'Tổng quan', stores: 'Shopify Stores', orders: 'Đơn hàng', products: 'Sản phẩm', inventory: 'Tồn kho', customers: 'Khách hàng', skus: 'SKU nội bộ', finance: 'Tài chính & lợi nhuận', suppliers: 'Supplier Management', procurement: 'Procurement Management', pim: 'Product Information Management', fulfillment: 'Fulfillment Management', operations: 'Vận hành nâng cao', people: 'Nhân sự & công ty', analytics: 'BI & tự động hóa', users: 'Người dùng', roles: 'Vai trò & quyền', audit: 'Nhật ký kiểm toán', settings: 'Cài đặt công ty' };

export function ErpShell({ section }: { section: Section }) {
  const router = useRouter();
  const pathname = usePathname();
  const [me, setMe] = useState<Me | null>(null);
  const [companyId, setCompanyId] = useState('');
  const [error, setError] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const [dark, setDark] = useState(false);
  useEffect(() => {
    const saved = localStorage.getItem('erp-theme') === 'dark';
    setDark(saved); document.documentElement.classList.toggle('dark', saved);
    api<Me>('/auth/me').then(user => {
      setMe(user);
      const savedCompany = localStorage.getItem('erp-company');
      const first = user.memberships.find(x => x.company.id === savedCompany)?.company.id || user.memberships[0]?.company.id || '';
      setCompanyId(first);
    }).catch(() => router.replace('/login'));
  }, [router]);
  const company = me?.memberships.find(x => x.company.id === companyId)?.company;
  const globalPermissions = useMemo(() => new Set(me?.assignments.filter(a => a.companyId === companyId && !a.storeId).flatMap(a => a.role.permissions.map(p => p.permissionCode)) || []), [me, companyId]);
  const permissions = useMemo(() => new Set(me?.assignments.filter(a => a.companyId === companyId).flatMap(a => a.role.permissions.map(p => p.permissionCode)) || []), [me, companyId]);
  const warehousePermissions = useMemo(() => new Set(me?.assignments.filter(a => a.companyId === companyId && !a.storeId).flatMap(a => a.role.permissions.map(p => p.permissionCode)) || []), [me, companyId]);
  const selectedLink = links.find(link => link.href === (section === 'overview' ? '/dashboard' : `/dashboard/${section}`));
  const selectedPermissions = selectedLink && 'scope' in selectedLink ? selectedLink.scope === 'store' ? permissions : warehousePermissions : globalPermissions;
  const canView = section === 'overview' ? globalPermissions.has('company.read') : !!selectedLink && selectedPermissions.has(selectedLink.permission);
  function toggleTheme() { const next = !dark; setDark(next); localStorage.setItem('erp-theme', next ? 'dark' : 'light'); document.documentElement.classList.toggle('dark', next); }
  async function logout() { try { await api('/auth/logout', { method: 'POST' }); } finally { router.replace('/login'); } }
  if (!me) return <div className="flex min-h-screen items-center justify-center bg-canvas text-muted">Đang tải không gian làm việc…</div>;
  return <div className="min-h-screen bg-canvas lg:pl-[258px]">
    {menuOpen && <button aria-label="Đóng menu" className="fixed inset-0 z-30 bg-black/50 lg:hidden" onClick={() => setMenuOpen(false)} />}
    <aside className={`fixed inset-y-0 left-0 z-40 flex w-[258px] flex-col border-r border-line bg-surface transition-transform lg:translate-x-0 ${menuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
      <div className="flex h-20 items-center justify-between border-b border-line px-6"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent text-white"><Layers3 size={21} /></div><div><div className="font-bold tracking-tight">Company ERP</div><div className="text-[10px] font-semibold uppercase tracking-[.16em] text-muted">Management Hub</div></div></div><button className="lg:hidden" onClick={() => setMenuOpen(false)} aria-label="Đóng"><X size={20} /></button></div>
      <div className="px-4 pt-6"><div className="rounded-xl border border-line bg-canvas px-3 py-3"><div className="mb-1 text-[10px] font-bold uppercase tracking-widest text-muted">Workspace</div><div className="flex items-center gap-2"><div className="flex h-8 w-8 items-center justify-center rounded-lg bg-accentsoft font-bold text-accent">{company?.name.charAt(0) || 'C'}</div><select aria-label="Chọn công ty" value={companyId} onChange={e => { setCompanyId(e.target.value); localStorage.setItem('erp-company', e.target.value); }} className="min-w-0 flex-1 bg-transparent text-sm font-semibold">{me.memberships.map(x => <option key={x.company.id} value={x.company.id}>{x.company.name}</option>)}</select><ChevronDown size={14} className="text-muted" /></div></div></div>
      <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-7"><p className="px-3 pb-3 text-[10px] font-bold uppercase tracking-[.2em] text-muted">Workspace</p>{links.filter(link => ('scope' in link ? link.scope === 'store' ? permissions : warehousePermissions : globalPermissions).has(link.permission)).map(link => { const Icon = link.icon; const active = pathname === link.href; return <Link key={link.href} href={link.href} onClick={() => setMenuOpen(false)} className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors ${active ? 'bg-accentsoft text-accent' : 'text-muted hover:bg-canvas hover:text-ink'}`}><Icon size={18} />{link.label}{active && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-accent" />}</Link>; })}</nav>
      <div className="border-t border-line p-4"><div className="flex items-center gap-3 rounded-xl bg-canvas p-3"><div className="flex h-9 w-9 items-center justify-center rounded-full bg-accentsoft text-accent"><CircleUserRound size={19} /></div><div className="min-w-0 flex-1"><div className="truncate text-sm font-bold">{me.name}</div><div className="truncate text-xs text-muted">{me.email}</div></div></div><button onClick={logout} className="mt-3 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-muted hover:bg-canvas hover:text-ink"><LogOut size={16} /> Đăng xuất</button></div>
    </aside>
      <div className="flex min-h-screen flex-col"><header className="sticky top-0 z-20 flex h-20 items-center justify-between border-b border-line bg-surface/95 px-5 backdrop-blur md:px-8"><div className="flex items-center gap-4"><button className="lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Mở menu"><Menu size={22} /></button><div><div className="text-xs text-muted">Company ERP / {company?.name}</div><h1 className="mt-0.5 text-lg font-bold tracking-tight md:text-xl">{title[section]}</h1></div></div><div className="flex items-center gap-2"><Badge className="hidden sm:inline-flex"><Activity size={12} className="mr-1" /> Full Design · Phase 7</Badge><Button variant="ghost" size="icon" onClick={toggleTheme} aria-label={dark ? 'Bật giao diện sáng' : 'Bật giao diện tối'}>{dark ? <Sun size={19} /> : <Moon size={19} />}</Button></div></header>
      <main className="mx-auto w-full max-w-[1440px] flex-1 px-5 py-8 md:px-8"><div className="mb-7 flex flex-wrap items-end justify-between gap-3"><div><p className="mb-2 text-xs font-bold uppercase tracking-[.18em] text-accent">{commerceSections.includes(section) ? 'Commerce Operations' : section === 'analytics' ? 'Business Intelligence' : 'Quản trị nền tảng'}</p><h2 className="text-2xl font-bold tracking-tight md:text-[28px]">{title[section]}</h2><p className="mt-2 text-sm text-muted">{section === 'overview' ? 'Tình trạng nền tảng và quyền truy cập của công ty.' : 'Dữ liệu được lưu và phân quyền theo công ty đang chọn.'}</p></div></div>
        {error && <div role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-red-700 dark:bg-red-950">{error}<button className="ml-3 underline" onClick={() => setError('')}>Đóng</button></div>}
        {companyId && canView ? commerceSections.includes(section) ? <CommerceView section={section} companyId={companyId} permissions={permissions} onError={setError} /> : lifecycleSections.includes(section) ? <LifecycleView module={section as 'suppliers' | 'procurement' | 'pim' | 'fulfillment'} companyId={companyId} permissions={section === 'procurement' || section === 'fulfillment' ? warehousePermissions : globalPermissions} onError={setError} /> : section === 'finance' ? <FinanceView companyId={companyId} permissions={globalPermissions} onError={setError} /> : section === 'operations' ? <OperationsView companyId={companyId} permissions={warehousePermissions} globalPermissions={globalPermissions} onError={setError} /> : section === 'people' ? <PeopleView companyId={companyId} permissions={globalPermissions} onError={setError} /> : section === 'analytics' ? <AnalyticsView companyId={companyId} permissions={globalPermissions} onError={setError} /> : <AdminView section={section} companyId={companyId} company={company!} me={me} permissions={globalPermissions} onError={setError} /> : <div className="rounded-xl border border-line bg-surface p-8 text-muted">Bạn không có quyền xem khu vực này.</div>}
      </main><footer className="border-t border-line px-8 py-5 text-xs text-muted">Company ERP · Full operations suite · Dữ liệu thực từ Shopify và các connector đã cấu hình</footer></div>
  </div>;
}
