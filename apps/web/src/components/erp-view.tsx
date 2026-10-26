'use client';
import { FormEvent, useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, Building2, Clock3, Plus, RefreshCw, Search, Shield, Store as StoreIcon, Users as UsersIcon, X } from 'lucide-react';
import { api, Audit, Company, Me, Role, Store, SyncJob, User, Warehouse } from '@/lib/api';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Card, CardContent, CardHeader } from './ui/card';
import { Dialog, DialogContent } from './ui/dialog';
import { Input } from './ui/input';
import type { Section } from './erp-shell';

type Props = { section: Section; companyId: string; company: Company; me: Me; permissions: Set<string>; onError: (message: string) => void };
const date = (value: string | null) => value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : 'Chưa có';
const Select = ({ children, ...props }: React.ComponentProps<'select'>) => <select className="h-10 w-full rounded-lg border border-line bg-surface px-3 text-ink" {...props}>{children}</select>;
const Field = ({ label, children }: { label: string; children: React.ReactNode }) => <label className="block space-y-2"><span className="text-sm font-semibold">{label}</span>{children}</label>;
const Empty = ({ text }: { text: string }) => <div className="rounded-xl border border-dashed border-line bg-surface p-12 text-center text-muted">{text}</div>;
const Table = ({ heads, children }: { heads: string[]; children: React.ReactNode }) => <div className="overflow-x-auto rounded-2xl border border-line bg-surface shadow-sm"><table className="w-full min-w-[700px] text-left text-sm"><thead className="border-b border-line bg-canvas/70 text-xs font-bold uppercase tracking-wide text-muted"><tr>{heads.map(x => <th key={x} className="px-5 py-4">{x}</th>)}</tr></thead><tbody className="divide-y divide-line">{children}</tbody></table></div>;
const Cell = ({ children, className = '' }: { children: React.ReactNode; className?: string }) => <td className={`px-5 py-4 ${className}`}>{children}</td>;

export function AdminView(props: Props) {
  switch (props.section) {
    case 'overview': return <Overview {...props} />;
    case 'users': return <UsersView {...props} />;
    case 'roles': return <RolesView {...props} />;
    case 'stores': return <StoresView {...props} />;
    case 'settings': return <SettingsView {...props} />;
    case 'audit': return <AuditView {...props} />;
  }
}

function Overview({ companyId, company, permissions, onError }: Props) {
  const [counts, setCounts] = useState<{ users?: number; stores?: number; roles?: number; audit?: number }>({});
  const load = useCallback(async () => {
    const endpoints = [
      ['users', 'users.read', '/users'], ['stores', 'stores.read', '/stores'], ['roles', 'roles.read', '/roles'], ['audit', 'audit.read', '/audit-logs'],
    ] as const;
    const result: Record<string, number> = {};
    await Promise.all(endpoints.filter(([, permission]) => permissions.has(permission)).map(async ([key, , path]) => {
      try { result[key] = (await api<unknown[]>(path, {}, companyId)).length; } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải dữ liệu'); }
    }));
    setCounts(result);
  }, [companyId, permissions, onError]);
  useEffect(() => { void load(); }, [load]);
  const cards = [
    { label: 'Người dùng', count: counts.users, icon: UsersIcon, href: '/dashboard/users', color: 'bg-blue-50 text-blue-700 dark:bg-blue-950' },
    { label: 'Cửa hàng', count: counts.stores, icon: StoreIcon, href: '/dashboard/stores', color: 'bg-amber-50 text-amber-700 dark:bg-amber-950' },
    { label: 'Vai trò', count: counts.roles, icon: Shield, href: '/dashboard/roles', color: 'bg-violet-50 text-violet-700 dark:bg-violet-950' },
    { label: 'Sự kiện audit gần đây', count: counts.audit, icon: Clock3, href: '/dashboard/audit', color: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950' },
  ];
  return <><div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards.map(card => <Link key={card.label} href={card.href}><Card className="h-full transition-transform hover:-translate-y-0.5"><CardContent className="p-5"><div className="flex items-start justify-between"><div className={`flex h-10 w-10 items-center justify-center rounded-xl ${card.color}`}><card.icon size={19} /></div><ArrowUpRight size={16} className="text-muted" /></div><div className="mt-6 text-3xl font-bold">{card.count ?? '—'}</div><div className="mt-1 text-sm text-muted">{card.label}</div></CardContent></Card></Link>)}</div>
    <div className="mt-6 grid gap-5 lg:grid-cols-[1.25fr_.75fr]"><Card><CardHeader><h3 className="text-lg font-bold">Không gian làm việc</h3><p className="text-sm text-muted">Cấu hình nền tảng của {company.name}</p></CardHeader><CardContent className="grid gap-4 pt-2 sm:grid-cols-2"><Info label="Mã công ty" value={company.code} /><Info label="Tiền tệ cơ sở" value={company.baseCurrency} /><Info label="Múi giờ" value={company.timezone} /><Info label="Bảo mật" value="Session + MFA + RBAC" /></CardContent></Card><Card className="border-accent/20 bg-accentsoft"><CardContent className="p-6"><div className="mb-5 flex h-11 w-11 items-center justify-center rounded-xl bg-accent text-white"><Building2 size={21} /></div><h3 className="text-lg font-bold">ERP đã sẵn sàng</h3><p className="mt-2 leading-6 text-muted">Các module Shopify, commerce, finance, operations, people và BI đã được bật theo quyền của người dùng.</p><Button asChild variant="outline" className="mt-6"><Link href="/dashboard/stores">Quản lý cửa hàng <ArrowUpRight size={15} /></Link></Button></CardContent></Card></div>
  </>;
}

function Info({ label, value }: { label: string; value: string }) { return <div className="rounded-xl border border-line bg-canvas p-4"><div className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</div><div className="mt-2 font-bold">{value}</div></div>; }

function UsersView({ companyId, permissions, onError, me }: Props) {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [search, setSearch] = useState('');
  const [open, setOpen] = useState(false);
  const [assigning, setAssigning] = useState<User | null>(null);
  const [editingUser, setEditingUser] = useState<User | null>(null);
  const [editName, setEditName] = useState('');
  const [form, setForm] = useState({ email: '', name: '', password: '', roleId: '' });
  const [assignment, setAssignment] = useState({ roleId: '', storeId: '', warehouseId: '' });
  const [stores, setStores] = useState<Store[]>([]);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const grantableRoles = roles.filter(role => role.permissions.every(p => permissions.has(p.permissionCode)));
  const load = useCallback(async () => {
    try { const [u, r, s, w] = await Promise.all([api<User[]>('/users', {}, companyId), permissions.has('roles.read') ? api<Role[]>('/roles', {}, companyId) : Promise.resolve([]), permissions.has('stores.read') ? api<Store[]>('/stores', {}, companyId) : Promise.resolve([]), permissions.has('inventory.read') ? api<Warehouse[]>('/operations/warehouses', {}, companyId) : Promise.resolve([])]); setUsers(u); setRoles(r); setStores(s); setWarehouses(w); }
    catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải người dùng'); }
  }, [companyId, onError, permissions]);
  useEffect(() => { void load(); }, [load]);
  async function create(event: FormEvent) { event.preventDefault(); try { await api('/users', { method: 'POST', body: JSON.stringify(form) }, companyId); setOpen(false); setForm({ email: '', name: '', password: '', roleId: '' }); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tạo người dùng'); } }
  async function toggle(user: User) { if (!confirm(`${user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'} tài khoản ${user.email}?`)) return; try { await api(`/users/${user.id}`, { method: 'PATCH', body: JSON.stringify({ status: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }) }, companyId); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể cập nhật'); } }
  async function assign(event: FormEvent) { event.preventDefault(); if (!assigning) return; try { await api(`/users/${assigning.id}/roles`, { method: 'POST', body: JSON.stringify({ roleId: assignment.roleId, ...(assignment.storeId ? { storeId: assignment.storeId } : {}), ...(assignment.warehouseId ? { warehouseId: assignment.warehouseId } : {}) }) }, companyId); setAssigning(null); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể gán vai trò'); } }
  async function revoke(user: User, assignmentId: string) { if (!confirm(`Thu hồi vai trò của ${user.email}?`)) return; try { await api(`/users/${user.id}/roles/${assignmentId}`, { method: 'DELETE' }, companyId); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể thu hồi vai trò'); } }
  async function saveUser(event: FormEvent) { event.preventDefault(); if (!editingUser) return; try { await api(`/users/${editingUser.id}`, { method: 'PATCH', body: JSON.stringify({ name: editName }) }, companyId); setEditingUser(null); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể cập nhật người dùng'); } }
  const filtered = users.filter(x => `${x.name} ${x.email}`.toLowerCase().includes(search.toLowerCase()));
  return <><div className="mb-5 flex flex-wrap items-center justify-between gap-3"><div className="relative w-full max-w-sm"><Search size={17} className="absolute left-3 top-3 text-muted" /><Input className="pl-10" placeholder="Tìm theo tên hoặc email" value={search} onChange={e => setSearch(e.target.value)} /></div>{permissions.has('users.write') && grantableRoles.length > 0 && <Button onClick={() => { setForm(x => ({ ...x, roleId: grantableRoles[0]?.id || '' })); setOpen(true); }}><Plus size={16} /> Thêm người dùng</Button>}</div>
    {filtered.length ? <Table heads={['Người dùng', 'Vai trò', 'MFA', 'Lần đăng nhập', 'Trạng thái', '']}>
      {filtered.map(user => <tr key={user.id}><Cell><div className="font-semibold">{user.name}</div><div className="mt-1 text-xs text-muted">{user.email}</div></Cell><Cell><div className="flex max-w-[260px] flex-wrap gap-1">{user.assignments.map(a => <Badge key={a.id} className="gap-1">{a.role.name}{a.storeId ? ' · Store' : a.warehouseId ? ' · Warehouse' : ''}{permissions.has('users.write') && user.id !== me.id && grantableRoles.some(r => r.id === a.role.id) && <button type="button" aria-label={`Thu hồi ${a.role.name} của ${user.name}`} onClick={() => revoke(user, a.id)}><X size={12} /></button>}</Badge>)}</div></Cell><Cell>{user.mfaEnabled ? 'Đã bật' : 'Chưa bật'}</Cell><Cell className="text-muted">{date(user.lastLoginAt)}</Cell><Cell><Badge className={user.status === 'ACTIVE' ? 'bg-green-50 text-green-700 dark:bg-green-950' : 'bg-red-50 text-red-700 dark:bg-red-950'}>{user.status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</Badge></Cell><Cell><div className="flex gap-1">{permissions.has('users.write') && <><Button size="sm" variant="ghost" onClick={() => { setEditingUser(user); setEditName(user.name); }}>Sửa</Button>{grantableRoles.length > 0 && <Button size="sm" variant="ghost" onClick={() => { setAssignment({ roleId: grantableRoles[0]?.id || '', storeId: '', warehouseId: '' }); setAssigning(user); }}>Gán quyền</Button>}<Button size="sm" variant="ghost" disabled={user.id === me.id} onClick={() => toggle(user)}>{user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</Button></>}</div></Cell></tr>)}</Table> : <Empty text="Chưa có người dùng phù hợp." />}
    <Dialog open={open} onOpenChange={setOpen}><DialogContent title="Thêm người dùng"><form onSubmit={create} className="space-y-4"><Field label="Họ tên"><Input required minLength={2} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Email"><Input required type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></Field><Field label="Mật khẩu ban đầu"><Input required type="password" minLength={12} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></Field><Field label="Vai trò"><Select required value={form.roleId} onChange={e => setForm({ ...form, roleId: e.target.value })}><option value="">Chọn vai trò</option>{grantableRoles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</Select></Field><p className="text-xs text-muted">Chuyển mật khẩu cho người dùng qua kênh bảo mật. Tài khoản quản trị sẽ thiết lập MFA ở lần đăng nhập đầu.</p><Button className="w-full">Tạo người dùng</Button></form></DialogContent></Dialog>
    <Dialog open={!!assigning} onOpenChange={value => { if (!value) setAssigning(null); }}><DialogContent title={`Gán vai trò · ${assigning?.name || ''}`}><form onSubmit={assign} className="space-y-4"><Field label="Vai trò"><Select required value={assignment.roleId} onChange={e => setAssignment({ ...assignment, roleId: e.target.value })}><option value="">Chọn vai trò</option>{grantableRoles.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}</Select></Field><Field label="Phạm vi Store"><Select value={assignment.storeId} onChange={e => setAssignment({ ...assignment, storeId: e.target.value, warehouseId: '' })}><option value="">Không giới hạn theo store</option>{stores.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</Select></Field><Field label="Phạm vi Warehouse"><Select value={assignment.warehouseId} onChange={e => setAssignment({ ...assignment, warehouseId: e.target.value, storeId: '' })}><option value="">Không giới hạn theo warehouse</option>{warehouses.map(w => <option key={w.id} value={w.id}>{w.code} · {w.name}</option>)}</Select></Field><p className="text-xs text-muted">Chọn tối đa một loại phạm vi. Để trống cả hai để áp dụng toàn công ty.</p><Button className="w-full">Gán vai trò</Button></form></DialogContent></Dialog>
    <Dialog open={!!editingUser} onOpenChange={value => { if (!value) setEditingUser(null); }}><DialogContent title={`Sửa người dùng · ${editingUser?.email || ''}`}><form onSubmit={saveUser} className="space-y-4"><Field label="Họ tên"><Input required minLength={2} value={editName} onChange={e => setEditName(e.target.value)} /></Field><Button className="w-full">Lưu thay đổi</Button></form></DialogContent></Dialog>
  </>;
}

function RolesView({ companyId, permissions, onError }: Props) {
  const [roles, setRoles] = useState<Role[]>([]);
  const [available, setAvailable] = useState<{ code: string; description: string }[]>([]);
  const [editing, setEditing] = useState<Role | 'new' | null>(null);
  const [form, setForm] = useState({ name: '', description: '', permissions: [] as string[] });
  const load = useCallback(async () => { try { const [r, p] = await Promise.all([api<Role[]>('/roles', {}, companyId), api<{ code: string; description: string }[]>('/roles/permissions', {}, companyId)]); setRoles(r); setAvailable(p); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải vai trò'); } }, [companyId, onError]);
  useEffect(() => { void load(); }, [load]);
  function open(role?: Role) { setEditing(role || 'new'); setForm(role ? { name: role.name, description: role.description || '', permissions: role.permissions.map(x => x.permissionCode) } : { name: '', description: '', permissions: [] }); }
  async function save(event: FormEvent) { event.preventDefault(); try { await api(editing === 'new' ? '/roles' : `/roles/${(editing as Role).id}`, { method: editing === 'new' ? 'POST' : 'PATCH', body: JSON.stringify(form) }, companyId); setEditing(null); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể lưu vai trò'); } }
  return <><div className="mb-5 flex justify-end">{permissions.has('roles.write') && <Button onClick={() => open()}><Plus size={16} /> Tạo vai trò</Button>}</div><div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">{roles.map(role => <Card key={role.id}><CardContent className="p-5"><div className="flex items-start justify-between gap-2"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accentsoft text-accent"><Shield size={19} /></div>{role.isSystem && <Badge>Hệ thống</Badge>}</div><h3 className="mt-5 text-lg font-bold">{role.name}</h3><p className="mt-1 min-h-10 text-sm text-muted">{role.description || `${role.permissions.length} quyền được cấp`}</p><div className="mt-5 flex items-center justify-between border-t border-line pt-4"><span className="text-xs text-muted">{role._count?.assignments ?? 0} lần gán · {role.permissions.length} quyền</span>{permissions.has('roles.write') && !role.isSystem && role.permissions.every(p => permissions.has(p.permissionCode)) && <Button size="sm" variant="outline" onClick={() => open(role)}>Chỉnh sửa</Button>}</div></CardContent></Card>)}</div>
    <Dialog open={!!editing} onOpenChange={value => { if (!value) setEditing(null); }}><DialogContent title={editing === 'new' ? 'Tạo vai trò' : 'Chỉnh sửa vai trò'}><form onSubmit={save} className="space-y-4"><Field label="Tên vai trò"><Input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Mô tả"><Input value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} /></Field><div><div className="mb-2 font-semibold">Quyền truy cập</div><div className="max-h-64 space-y-2 overflow-y-auto rounded-lg border border-line p-3">{available.filter(p => permissions.has(p.code)).map(p => <label key={p.code} className="flex items-start gap-3 rounded-md p-2 hover:bg-canvas"><input type="checkbox" checked={form.permissions.includes(p.code)} onChange={e => setForm({ ...form, permissions: e.target.checked ? [...form.permissions, p.code] : form.permissions.filter(x => x !== p.code) })} className="mt-1 accent-[#c46a45]" /><span><strong className="block text-sm">{p.code}</strong><span className="text-xs text-muted">{p.description}</span></span></label>)}</div></div><Button className="w-full">Lưu vai trò</Button></form></DialogContent></Dialog>
  </>;
}

function StoresView({ companyId, permissions, onError }: Props) {
  const [stores, setStores] = useState<Store[]>([]);
  const [jobs, setJobs] = useState<Record<string, SyncJob[]>>({});
  const [busy, setBusy] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: '', shopifyDomain: '', currency: 'USD' });
  const load = useCallback(async () => { try { setStores(await api<Store[]>('/stores', {}, companyId)); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải cửa hàng'); } }, [companyId, onError]);
  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const error = params.get('shopify_error');
    const connected = params.get('shopify') === 'connected';
    if (error) onError(`Không thể kết nối Shopify: ${error}`);
    if (connected) onError('Đã kết nối Shopify thành công. Dữ liệu đang được đưa vào hàng đợi đồng bộ.');
    if (error || connected) window.history.replaceState({}, '', window.location.pathname);
  }, [onError]);
  async function create(event: FormEvent) { event.preventDefault(); try { await api('/stores', { method: 'POST', body: JSON.stringify(form) }, companyId); setOpen(false); setForm({ name: '', shopifyDomain: '', currency: 'USD' }); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể lưu store'); } }
  async function toggle(store: Store) { if (!confirm(`${store.status === 'DISABLED' ? 'Bật' : 'Tắt'} cấu hình ${store.name}?`)) return; try { await api(`/stores/${store.id}`, { method: 'PATCH', body: JSON.stringify({ status: store.status === 'DISABLED' ? 'DRAFT' : 'DISABLED' }) }, companyId); await load(); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể cập nhật'); } }
  async function connect(store: Store) { try { setBusy(store.id); const result = await api<{ authorizationUrl: string }>(`/stores/${store.id}/shopify/connect`, { method: 'POST' }, companyId); window.location.assign(result.authorizationUrl); } catch (e) { setBusy(null); onError(e instanceof Error ? e.message : 'Không thể bắt đầu kết nối Shopify'); } }
  async function sync(store: Store) { try {
    setBusy(store.id);
    const created = await api<SyncJob[]>(`/stores/${store.id}/shopify/sync`, { method: 'POST' }, companyId);
    const ids = new Set(created.map(job => job.id));
    for (let attempt = 0; attempt < 60; attempt += 1) {
      const data = await api<SyncJob[]>(`/stores/${store.id}/shopify/sync-jobs`, {}, companyId);
      setJobs(previous => ({ ...previous, [store.id]: data }));
      const current = data.filter(job => ids.has(job.id));
      if (current.length === created.length && current.every(job => job.status === 'COMPLETED' || job.status === 'FAILED')) break;
      await new Promise(resolve => setTimeout(resolve, 2000));
    }
  } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tạo tác vụ đồng bộ'); } finally { setBusy(null); } }
  async function showJobs(store: Store) { try { const data = await api<SyncJob[]>(`/stores/${store.id}/shopify/sync-jobs`, {}, companyId); setJobs(previous => ({ ...previous, [store.id]: data })); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải lịch sử đồng bộ'); } }
  async function retry(store: Store, job: SyncJob) { try { await api(`/stores/${store.id}/shopify/sync-jobs/${job.id}/retry`, { method: 'POST' }, companyId); await showJobs(store); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể thử lại tác vụ'); } }
  return <><div className="mb-5 flex items-center justify-between gap-4"><p className="text-sm text-muted">Kết nối OAuth, đồng bộ sản phẩm, đơn hàng và tồn kho theo từng store.</p>{permissions.has('stores.write') && <Button onClick={() => setOpen(true)}><Plus size={16} /> Thêm store</Button>}</div>{stores.length ? <div className="space-y-4">{stores.map(store => <Card key={store.id}><CardContent className="p-0"><div className="grid items-center gap-4 p-5 md:grid-cols-[1.2fr_1fr_auto]"><div className="flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accentsoft text-accent"><StoreIcon size={19} /></div><div><div className="font-bold">{store.name}</div><div className="font-mono text-xs text-muted">{store.shopifyDomain}</div></div></div><div><Badge className={store.status === 'CONNECTED' ? 'bg-green-50 text-green-700 dark:bg-green-950 dark:text-green-300' : ''}>{store.status === 'DRAFT' ? 'Chưa kết nối' : store.status === 'DISABLED' ? 'Đã tắt' : 'Đã kết nối'}</Badge><div className="mt-1 text-xs text-muted">{store.connection ? `${store.connection.scopes.length} quyền · kết nối ${date(store.connection.installedAt)}` : `${store.currency} · tạo ${date(store.createdAt)}`}</div></div><div className="flex flex-wrap justify-end gap-2">{store.status === 'DRAFT' && permissions.has('stores.write') && <Button size="sm" disabled={busy === store.id} onClick={() => connect(store)}><ArrowUpRight size={15} /> Kết nối Shopify</Button>}{store.status === 'CONNECTED' && <Button size="sm" variant="outline" onClick={() => showJobs(store)}><Clock3 size={15} /> Lịch sử</Button>}{store.status === 'CONNECTED' && permissions.has('stores.write') && <Button size="sm" disabled={busy === store.id} onClick={() => sync(store)}><RefreshCw size={15} /> Đồng bộ</Button>}{permissions.has('stores.write') && <Button size="sm" variant="ghost" onClick={() => toggle(store)}>{store.status === 'DISABLED' ? 'Bật' : 'Tắt'}</Button>}</div></div>{jobs[store.id] && <div className="border-t border-line bg-canvas/40 px-5 py-4"><div className="mb-3 flex items-center justify-between"><span className="text-sm font-bold">Tác vụ gần đây</span><div className="flex gap-1"><Button size="sm" variant="ghost" onClick={() => showJobs(store)}><RefreshCw size={15} /> Làm mới</Button><Button size="sm" variant="ghost" onClick={() => setJobs(previous => { const next = { ...previous }; delete next[store.id]; return next; })}><X size={15} /> Đóng</Button></div></div>{jobs[store.id].length ? <div className="space-y-2">{jobs[store.id].slice(0, 8).map(job => <div key={job.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-line bg-surface px-3 py-2 text-sm"><span className="font-semibold">{job.kind}</span><span className="text-muted">{job.processed} bản ghi · {date(job.createdAt)}</span><div className="flex items-center gap-2"><Badge className={job.status === 'COMPLETED' ? 'bg-green-50 text-green-700' : job.status === 'FAILED' ? 'bg-red-50 text-red-700' : ''}>{job.status}</Badge>{job.status === 'FAILED' && permissions.has('stores.write') && <Button size="sm" variant="ghost" onClick={() => retry(store, job)}>Thử lại</Button>}</div>{job.error && <div className="w-full text-xs text-red-700">{job.error}</div>}</div>)}</div> : <p className="text-sm text-muted">Chưa có tác vụ đồng bộ.</p>}</div>}</CardContent></Card>)}</div> : <Empty text="Chưa có cửa hàng. Thêm store để kết nối Shopify." />}
    <Dialog open={open} onOpenChange={setOpen}><DialogContent title="Thêm Shopify Store"><form onSubmit={create} className="space-y-4"><Field label="Tên hiển thị"><Input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Domain Shopify"><Input required placeholder="example.myshopify.com" pattern="[a-z0-9][a-z0-9-]*\.myshopify\.com" value={form.shopifyDomain} onChange={e => setForm({ ...form, shopifyDomain: e.target.value.toLowerCase() })} /></Field><Field label="Tiền tệ"><Input required maxLength={3} pattern="[A-Z]{3}" value={form.currency} onChange={e => setForm({ ...form, currency: e.target.value.toUpperCase() })} /></Field><p className="text-xs text-muted">Sau khi lưu, dùng nút Kết nối Shopify để cấp quyền OAuth.</p><Button className="w-full">Lưu cửa hàng</Button></form></DialogContent></Dialog>
  </>;
}

function SettingsView({ companyId, company, permissions, onError, me }: Props) {
  const [form, setForm] = useState({ name: company.name, baseCurrency: company.baseCurrency, timezone: company.timezone });
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' });
  const [saved, setSaved] = useState(false);
  useEffect(() => { setForm({ name: company.name, baseCurrency: company.baseCurrency, timezone: company.timezone }); }, [company]);
  async function save(event: FormEvent) { event.preventDefault(); try { await api('/company', { method: 'PATCH', body: JSON.stringify(form) }, companyId); setSaved(true); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể cập nhật công ty'); } }
  async function changePassword(event: FormEvent) { event.preventDefault(); try { await api('/auth/password', { method: 'POST', body: JSON.stringify(passwords) }); window.location.href = '/login'; } catch (e) { onError(e instanceof Error ? e.message : 'Không thể đổi mật khẩu'); } }
  return <div className="grid gap-5 lg:grid-cols-2"><Card><CardHeader><h3 className="text-lg font-bold">Thông tin công ty</h3><p className="text-sm text-muted">Cấu hình phạm vi dữ liệu và báo cáo.</p></CardHeader><CardContent><form onSubmit={save} className="space-y-4"><Field label="Tên công ty"><Input required disabled={!permissions.has('company.write')} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></Field><Field label="Mã công ty"><Input disabled value={company.code} /></Field><div className="grid grid-cols-2 gap-4"><Field label="Tiền tệ cơ sở"><Input required maxLength={3} disabled={!permissions.has('company.write')} value={form.baseCurrency} onChange={e => setForm({ ...form, baseCurrency: e.target.value.toUpperCase() })} /></Field><Field label="Múi giờ"><Input required disabled={!permissions.has('company.write')} value={form.timezone} onChange={e => setForm({ ...form, timezone: e.target.value })} /></Field></div>{permissions.has('company.write') && <Button>Lưu thay đổi</Button>}{saved && <span className="ml-3 text-sm text-green-700">Đã lưu</span>}</form></CardContent></Card><Card><CardHeader><h3 className="text-lg font-bold">Bảo mật tài khoản</h3><p className="text-sm text-muted">{me.email} · MFA {me.mfaEnabled ? 'đang bật' : 'chưa bật'}</p></CardHeader><CardContent><form onSubmit={changePassword} className="space-y-4"><Field label="Mật khẩu hiện tại"><Input type="password" required value={passwords.currentPassword} onChange={e => setPasswords({ ...passwords, currentPassword: e.target.value })} /></Field><Field label="Mật khẩu mới"><Input type="password" required minLength={12} value={passwords.newPassword} onChange={e => setPasswords({ ...passwords, newPassword: e.target.value })} /></Field><Button variant="outline">Đổi mật khẩu</Button></form>{!me.mfaEnabled && <Button variant="secondary" className="mt-5" onClick={async () => { try { await api('/auth/mfa/setup', { method: 'POST' }); window.location.href = '/mfa?step=mfa-setup'; } catch (e) { onError(e instanceof Error ? e.message : 'Không thể bật MFA'); } }}>Bật MFA</Button>}</CardContent></Card></div>;
}

function AuditView({ companyId, onError }: Props) {
  const [logs, setLogs] = useState<Audit[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const load = useCallback(async (next?: string) => { try { const data = await api<Audit[]>(`/audit-logs${next ? `?cursor=${encodeURIComponent(next)}` : ''}`, {}, companyId); setLogs(previous => next ? [...previous, ...data] : data); setCursor(data.length === 50 ? data[data.length - 1].id : null); } catch (e) { onError(e instanceof Error ? e.message : 'Không thể tải audit logs'); } }, [companyId, onError]);
  useEffect(() => { void load(); }, [load]);
  return <><div className="mb-5 flex justify-end"><Button variant="outline" onClick={() => load()}><RefreshCw size={16} /> Làm mới</Button></div>{logs.length ? <Table heads={['Thời gian', 'Hành động', 'Đối tượng', 'Người thực hiện']}>
    {logs.map(log => <tr key={log.id}><Cell className="text-muted">{date(log.createdAt)}</Cell><Cell><Badge>{log.action}</Badge></Cell><Cell><div className="font-semibold">{log.resourceType}</div><div className="font-mono text-xs text-muted">{log.resourceId}</div></Cell><Cell>{log.actor?.name || 'Hệ thống'}<div className="text-xs text-muted">{log.actor?.email}</div></Cell></tr>)}</Table> : <Empty text="Chưa có sự kiện kiểm toán." />}{cursor && <div className="mt-5 text-center"><Button variant="outline" onClick={() => load(cursor)}>Xem thêm</Button></div>}</>;
}
