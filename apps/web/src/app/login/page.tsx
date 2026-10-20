'use client';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowRight, Layers3, LockKeyhole, Moon, ShieldCheck } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  async function submit(event: FormEvent) {
    event.preventDefault(); setError(''); setBusy(true);
    try {
      const result = await api<{ next: string }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
      router.replace(result.next === 'app' ? '/dashboard' : `/mfa?step=${result.next}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể đăng nhập'); }
    finally { setBusy(false); }
  }
  return <main className="grid min-h-screen lg:grid-cols-[1.04fr_.96fr]">
    <section className="relative hidden overflow-hidden bg-[#16212a] p-12 text-white lg:flex lg:flex-col lg:justify-between">
      <div className="absolute -right-36 -top-40 h-[530px] w-[530px] rounded-full border border-white/10" />
      <div className="absolute -right-20 -top-20 h-[370px] w-[370px] rounded-full border border-white/10" />
      <div className="absolute bottom-[-160px] left-[-80px] h-[430px] w-[430px] rounded-full bg-[#c46a45]/25 blur-3xl" />
      <div className="relative flex items-center gap-3"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[#c46a45]"><Layers3 size={22} /></div><span className="text-lg font-bold tracking-tight">Company ERP</span></div>
      <div className="relative max-w-xl"><div className="mb-8 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-xs font-semibold uppercase tracking-[.18em] text-[#f2c4af]"><ShieldCheck size={15} /> Internal operations</div><h1 className="text-5xl font-bold leading-[1.12] tracking-tight">Một nơi để vận hành<br /><span className="text-[#e8a182]">toàn bộ doanh nghiệp.</span></h1><p className="mt-6 max-w-md text-base leading-7 text-slate-300">Quản trị con người, quyền truy cập và các cửa hàng Shopify trong một nền tảng nhất quán.</p></div>
      <div className="relative flex items-center gap-3 text-xs text-slate-400"><LockKeyhole size={15} /> Dữ liệu nội bộ được bảo vệ theo vai trò và công ty</div>
    </section>
    <section className="flex min-h-screen items-center justify-center bg-canvas px-6 py-12"><div className="w-full max-w-[420px]">
      <div className="mb-12 flex items-center gap-3 lg:hidden"><div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent text-white"><Layers3 size={20} /></div><span className="text-lg font-bold">Company ERP</span></div>
      <div className="mb-8 flex h-12 w-12 items-center justify-center rounded-2xl bg-accentsoft text-accent"><Moon size={23} /></div>
      <p className="mb-2 text-xs font-bold uppercase tracking-[.2em] text-accent">Chào mừng trở lại</p><h2 className="text-3xl font-bold tracking-tight">Đăng nhập</h2><p className="mt-3 text-muted">Sử dụng tài khoản do quản trị viên cấp để tiếp tục.</p>
      <form onSubmit={submit} className="mt-9 space-y-5"><label className="block space-y-2"><span className="text-sm font-semibold">Email công việc</span><Input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required placeholder="name@company.com" /></label><label className="block space-y-2"><span className="text-sm font-semibold">Mật khẩu</span><Input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required placeholder="••••••••••••" /></label>
        {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-950">{error}</p>}
        <Button disabled={busy} className="mt-2 w-full">{busy ? 'Đang xác thực…' : 'Đăng nhập'} <ArrowRight size={17} /></Button>
      </form><p className="mt-10 text-xs text-muted">Nếu cần quyền truy cập, liên hệ quản trị viên hệ thống.</p>
    </div></section>
  </main>;
}
