'use client';
import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowRight, ShieldCheck } from 'lucide-react';
import { api } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';

const mfaSchema = z.object({ code: z.string().regex(/^\d{6}$/, 'Nhập đúng mã gồm 6 chữ số') });
type MfaFormValues = z.infer<typeof mfaSchema>;

function MfaForm() {
  const router = useRouter();
  const params = useSearchParams();
  const setup = params.get('step') === 'mfa-setup';
  const [secret, setSecret] = useState('');
  const [error, setError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<MfaFormValues>({ resolver: zodResolver(mfaSchema), defaultValues: { code: '' } });
  useEffect(() => { if (setup) api<{ secret: string }>('/auth/mfa/setup', { method: 'POST' }).then(x => setSecret(x.secret)).catch(e => setError(e.message)); }, [setup]);
  async function submit(values: MfaFormValues) {
    setError('');
    try { await api(setup ? '/auth/mfa/confirm' : '/auth/mfa/verify', { method: 'POST', body: JSON.stringify(values) }); router.replace('/dashboard'); }
    catch (cause) { setError(cause instanceof Error ? cause.message : 'Mã không hợp lệ'); }
  }
  return <main className="flex min-h-screen items-center justify-center bg-canvas px-4"><Card className="w-full max-w-md"><CardContent className="p-8"><div className="mb-6 flex h-12 w-12 items-center justify-center rounded-xl bg-accentsoft text-accent"><ShieldCheck /></div><h1 className="text-2xl font-bold">{setup ? 'Bật xác thực hai bước' : 'Xác thực hai bước'}</h1><p className="mt-2 text-muted">{setup ? 'Thêm khóa bên dưới vào ứng dụng Authenticator, sau đó nhập mã 6 số để hoàn tất.' : 'Nhập mã 6 số trong ứng dụng Authenticator của bạn.'}</p>
    {setup && <div className="mt-6 rounded-lg border border-line bg-canvas p-4"><div className="text-xs font-semibold uppercase text-muted">Khóa thiết lập</div><div className="mt-2 break-all font-mono text-sm font-bold tracking-wider">{secret || 'Đang tạo khóa…'}</div><p className="mt-3 text-xs text-muted">Chỉ hiển thị trong 10 phút. Lưu khóa an toàn và không chia sẻ.</p></div>}
    <form onSubmit={handleSubmit(submit)} className="mt-6 space-y-4" noValidate><label className="block space-y-2"><span className="font-semibold">Mã xác thực</span><Input inputMode="numeric" maxLength={6} autoComplete="one-time-code" {...register('code')} aria-invalid={!!errors.code} placeholder="000000" />{errors.code && <span className="text-xs text-red-600">{errors.code.message}</span>}</label>{error && <p role="alert" className="text-sm text-red-600">{error}</p>}<Button className="w-full" disabled={isSubmitting || (setup && !secret)}>Tiếp tục <ArrowRight size={16} /></Button></form></CardContent></Card></main>;
}
export default function MfaPage() { return <Suspense><MfaForm /></Suspense>; }
