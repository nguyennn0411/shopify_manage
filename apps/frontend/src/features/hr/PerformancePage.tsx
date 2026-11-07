import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Star } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const cycleSchema = z.object({ name: z.string().trim().min(1).max(180), cycleType: z.string().trim().min(1).max(30), startsOn: z.string().min(1), endsOn: z.string().min(1) });
const reviewSchema = z.object({ cycleId: z.string().uuid(), employeeId: z.string().uuid(), reviewerEmployeeId: z.string().uuid(),
  reviewType: z.enum(['SELF', 'MANAGER', 'PEER', 'HR', '360']), score: z.number().min(0).max(100),
  strengths: z.string().trim().max(4000).optional(), improvements: z.string().trim().max(4000).optional(), developmentPlan: z.string().trim().max(4000).optional() });
const objectiveSchema = z.object({ cycleId: z.string().uuid(), employeeId: z.string().uuid(), objectiveType: z.enum(['KPI', 'OKR']), title: z.string().trim().min(1).max(255), targetValue: z.number().min(0).optional(), weight: z.number().min(0).max(100).optional() });
const feedbackSchema = z.object({ employeeId: z.string().uuid(), feedback: z.string().trim().min(1).max(4000), visibility: z.enum(['EMPLOYEE_ONLY', 'EMPLOYEE_AND_HR', 'MANAGEMENT']) });

export function PerformancePage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [tab, setTab] = useState<'reviews' | 'cycles'>('reviews');
  const reviews = useQuery({ queryKey: ['hr-performance-reviews', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/performance', { params: { companyId } })).data });
  const cycles = useQuery({ queryKey: ['hr-performance-cycles', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/performance/cycles', { params: { companyId } })).data });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['hr-performance-reviews', companyId] });
  const canWrite = can('hr.performance.write');
  const decide = useMutation({ mutationFn: async (values: { reviewId: string; status: string }) =>
      (await api.post<Created>(`/hr/performance/reviews/${values.reviewId}/decision`, { status: values.status }, { params: { companyId } })).data,
    onSuccess: invalidate });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / performance</span><h1>Đánh giá nhân viên</h1><p>KPI/OKR, đánh giá định kỳ, 360° và kế hoạch phát triển.</p></div>
    <div className="button-row"><button className={tab === 'reviews' ? 'primary' : 'secondary'} onClick={() => setTab('reviews')}>Đánh giá</button><button className={tab === 'cycles' ? 'primary' : 'secondary'} onClick={() => setTab('cycles')}>Kỳ đánh giá</button></div>
    {tab === 'reviews' && <section className="panel table-panel"><h2>Bảng đánh giá</h2>{reviews.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Loại</th><th>Điểm</th><th>Trạng thái</th><th>Nộp</th>{canWrite && <th>Duyệt</th>}</tr></thead><tbody>{reviews.data.map(row => <tr key={String(row.id)}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.review_type)}</td><td>{String(row.score ?? '—')}</td><td><span className="status-pill">{String(row.status)}</span></td><td>{String(row.submitted_at || '—')}</td>{canWrite && String(row.status) === 'DRAFT' && <td><div className="button-row"><button className="secondary" onClick={() => decide.mutate({ reviewId: String(row.id), status: 'SUBMITTED' })}>Nộp</button><button className="secondary" onClick={() => decide.mutate({ reviewId: String(row.id), status: 'COMPLETED' })}>Hoàn tất</button></div></td>}</tr>)}</tbody></table></div> : <div className="empty-table"><Star /><p>Chưa có bảng đánh giá.</p></div>}</section>}
    {tab === 'cycles' && <section className="panel table-panel"><h2>Kỳ đánh giá</h2>{cycles.data?.length ? <div className="table-scroll"><table><thead><tr><th>Tên</th><th>Loại</th><th>Thời gian</th><th>Trạng thái</th></tr></thead><tbody>{cycles.data.map(row => <tr key={String(row.id)}><td><strong>{String(row.name)}</strong></td><td>{String(row.cycle_type)}</td><td>{String(row.starts_on)} → {String(row.ends_on)}</td><td><span className="status-pill">{String(row.status)}</span></td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có kỳ đánh giá.</p></div>}</section>}
    {can('hr.performance.write') && <PerformanceForms tab={tab} companyId={companyId} onCreated={invalidate} />}
  </>;
}

function PerformanceForms({ tab, companyId, onCreated }: { tab: 'reviews' | 'cycles'; companyId: string; onCreated: () => void }) {
  const queryClient = useQueryClient();
  const after = () => { onCreated(); void queryClient.invalidateQueries({ queryKey: ['hr-performance-cycles', companyId] }); };
  const cycle = useForm<z.infer<typeof cycleSchema>>({ resolver: zodResolver(cycleSchema), defaultValues: { name: '', cycleType: 'QUARTERLY', startsOn: '', endsOn: '' } });
  const objective = useForm<z.infer<typeof objectiveSchema>>({ resolver: zodResolver(objectiveSchema), defaultValues: { cycleId: '', employeeId: '', objectiveType: 'KPI', title: '' } });
  const cycleMutation = useMutation({ mutationFn: async (values: z.infer<typeof cycleSchema>) => (await api.post<Created>('/hr/performance/cycles', values, { params: { companyId } })).data, onSuccess: () => { cycle.reset(); after(); } });
  const objectiveMutation = useMutation({ mutationFn: async (values: z.infer<typeof objectiveSchema>) => (await api.post<Created>('/hr/performance/objectives', values, { params: { companyId } })).data, onSuccess: () => { objective.reset(); after(); } });
  const panel = 'panel form-grid compact-form';
  if (tab === 'cycles') return <form className={panel} onSubmit={cycle.handleSubmit(values => cycleMutation.mutate(values))}><span className="form-section-title">Tạo kỳ đánh giá</span><label>Tên<input {...cycle.register('name')} /></label><label>Loại<input {...cycle.register('cycleType')} /></label><label>Bắt đầu<input type="date" {...cycle.register('startsOn')} /></label><label>Kết thúc<input type="date" {...cycle.register('endsOn')} /></label><div className="form-actions"><button className="primary" disabled={cycleMutation.isPending}>Lưu kỳ</button>{cycleMutation.error && <span className="form-error">{cycleMutation.error.message}</span>}</div></form>;
  return <><form className={panel} onSubmit={objective.handleSubmit(values => objectiveMutation.mutate(values))}><span className="form-section-title">Thêm KPI/OKR</span><label>UUID kỳ<input {...objective.register('cycleId')} /></label><label>UUID nhân viên<input {...objective.register('employeeId')} /></label><label>Loại<select {...objective.register('objectiveType')}>{['KPI', 'OKR'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><label>Tiêu đề<input {...objective.register('title')} /></label><div className="form-actions"><button className="primary" disabled={objectiveMutation.isPending}>Lưu mục tiêu</button>{objectiveMutation.error && <span className="form-error">{objectiveMutation.error.message}</span>}</div></form>
    <ReviewForm companyId={companyId} after={after} />
    <FeedbackForm companyId={companyId} after={after} /></>;
}

function ReviewForm({ companyId, after }: { companyId: string; after: () => void }) {
  const form = useForm<z.infer<typeof reviewSchema>>({ resolver: zodResolver(reviewSchema), defaultValues: { cycleId: '', employeeId: '', reviewerEmployeeId: '', reviewType: 'MANAGER', score: 0 } });
  const mutation = useMutation({ mutationFn: async (values: z.infer<typeof reviewSchema>) => (await api.post<Created>('/hr/performance/reviews', values, { params: { companyId } })).data,
    onSuccess: () => { form.reset(); after(); } });
  return <form className="panel form-grid compact-form" onSubmit={form.handleSubmit(values => mutation.mutate(values))}><span className="form-section-title">Tạo bảng đánh giá</span><label>UUID kỳ<input {...form.register('cycleId')} /></label><label>UUID nhân viên<input {...form.register('employeeId')} /></label><label>UUID người đánh giá<input {...form.register('reviewerEmployeeId')} /></label><label>Loại<select {...form.register('reviewType')}>{['SELF', 'MANAGER', 'PEER', 'HR', '360'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><label>Điểm (0–100)<input type="number" min={0} max={100} {...form.register('score', { valueAsNumber: true })} /></label><label className="wide-field">Điểm mạnh<textarea rows={2} {...form.register('strengths')} /></label><label className="wide-field">Cần cải thiện<textarea rows={2} {...form.register('improvements')} /></label><label className="wide-field">Kế hoạch phát triển<textarea rows={2} {...form.register('developmentPlan')} /></label><div className="form-actions"><button className="primary" disabled={mutation.isPending}>Lưu đánh giá</button>{mutation.error && <span className="form-error">{mutation.error.message}</span>}</div></form>;
}

function FeedbackForm({ companyId, after }: { companyId: string; after: () => void }) {
  const form = useForm<z.infer<typeof feedbackSchema>>({ resolver: zodResolver(feedbackSchema), defaultValues: { employeeId: '', feedback: '', visibility: 'EMPLOYEE_AND_HR' } });
  const mutation = useMutation({ mutationFn: async (values: z.infer<typeof feedbackSchema>) => (await api.post<Created>('/hr/performance/feedback', values, { params: { companyId } })).data, onSuccess: () => { form.reset(); after(); } });
  return <form className="panel form-grid compact-form" onSubmit={form.handleSubmit(values => mutation.mutate(values))}><span className="form-section-title">Phản hồi 360°</span><label>UUID nhân viên<input {...form.register('employeeId')} /></label><label className="wide-field">Phản hồi<textarea rows={2} {...form.register('feedback')} /></label><label>Hiển thị<select {...form.register('visibility')}>{['EMPLOYEE_ONLY', 'EMPLOYEE_AND_HR', 'MANAGEMENT'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><div className="form-actions"><button className="primary" disabled={mutation.isPending}>Gửi phản hồi</button>{mutation.error && <span className="form-error">{mutation.error.message}</span>}</div></form>;
}
