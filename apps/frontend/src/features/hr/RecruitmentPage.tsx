import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { UserPlus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
type Tab = 'positions' | 'candidates' | 'interviews' | 'offers';
const tabLabels: Record<Tab, string> = { positions: 'Vị trí', candidates: 'Ứng viên', interviews: 'Phỏng vấn', offers: 'Thư mời' };

export function RecruitmentPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth();
  const [tab, setTab] = useState<Tab>('positions');
  const rows = useRows(tab, companyId);
  const write = can('hr.recruitment.write');
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / recruitment</span><h1>Tuyển dụng</h1><p>Vị trí tuyển, ứng viên, vòng phỏng vấn và thư mời nhận việc. Thông tin liên lạc ứng viên được mã hóa.</p></div>
    <div className="button-row">{(Object.keys(tabLabels) as Tab[]).map(name => <button key={name} className={tab === name ? 'primary' : 'secondary'} onClick={() => setTab(name)}>{tabLabels[name]}</button>)}</div>
    <section className="panel table-panel"><h2>{tabLabels[tab]}</h2>{rows.data?.length ? <div className="table-scroll"><table><thead><tr>{headers[tab].map(column => <th key={column}>{column.replaceAll('_', ' ')}</th>)}{write && <th>Thao tác</th>}</tr></thead><tbody>{rows.data.map((row, index) => <tr key={String(row.id || index)}>{headers[tab].map(column => <td key={column}>{cell(tab, column, row)}</td>)}{write && <td><RowActions tab={tab} row={row} companyId={companyId} /></td>}</tr>)}</tbody></table></div> : <div className="empty-table"><UserPlus /><p>Chưa có dữ liệu.</p></div>}</section>
    {write && <FormPanel tab={tab} companyId={companyId} />}
  </>;
}

function useRows(tab: Tab, companyId: string) {
  const url = tab === 'positions' ? '/hr/recruitment' : tab === 'candidates' ? '/hr/recruitment/candidates' : tab === 'interviews' ? '/hr/recruitment/interviews' : '/hr/recruitment/offers';
  return useQuery({ queryKey: ['hr-recruitment', tab, companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>(url, { params: { companyId } })).data });
}

const headers: Record<Tab, string[]> = {
  positions: ['code', 'title', 'openings', 'status', 'closes_on'],
  candidates: ['full_name', 'position_title', 'stage', 'source', 'cv_object_key'],
  interviews: ['candidate_name', 'round_name', 'scheduled_at', 'location_or_url', 'status', 'score'],
  offers: ['candidate_name', 'proposed_title', 'proposed_start_date', 'status', 'responded_at'],
};

function cell(tab: Tab, column: string, row: Row) {
  if (column === 'cv_object_key') return row.cv_object_key ? <span className="success-text">Có CV</span> : '—';
  if (column === 'status' || column === 'stage' || column === 'score') return <span className="status-pill">{String(row[column] ?? '—')}</span>;
  return String(row[column] ?? '—');
}

function RowActions({ tab, row, companyId }: { tab: Tab; row: Row; companyId: string }) {
  const { can } = useAuth(); const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['hr-recruitment', tab, companyId] });
  const evaluate = useMutation({ mutationFn: async (values: { id: string; score: number; recommendation: string }) =>
      (await api.post<Created>(`/hr/recruitment/interviews/${values.id}/evaluate`, { score: values.score, recommendation: values.recommendation }, { params: { companyId } })).data,
    onSuccess: invalidate });
  const stage = useMutation({ mutationFn: async (values: { id: string; stage: string }) =>
      (await api.post<Created>(`/hr/recruitment/candidates/${values.id}/stage`, { stage: values.stage }, { params: { companyId } })).data,
    onSuccess: invalidate });
  const offer = useMutation({ mutationFn: async (values: { id: string; decision: string }) =>
      (await api.post<Created>(`/hr/recruitment/offers/${values.id}/decision`, { decision: values.decision }, { params: { companyId } })).data,
    onSuccess: invalidate });
  if (!can('hr.recruitment.write')) return null;
  if (tab === 'interviews' && String(row.status) === 'SCHEDULED') {
    return <div className="button-row"><button className="secondary" onClick={() => evaluate.mutate({ id: String(row.id), score: 80, recommendation: 'ADVANCE' })}>Đạt</button><button className="secondary" onClick={() => evaluate.mutate({ id: String(row.id), score: 40, recommendation: 'REJECT' })}>Từ chối</button></div>;
  }
  if (tab === 'candidates' && String(row.status) === 'ACTIVE') {
    const current = String(row.stage);
    const next = current === 'APPLIED' ? 'SCREENING' : current === 'SCREENING' ? 'INTERVIEW' : current === 'INTERVIEW' ? 'OFFER' : current;
    if (next === current) return <span className="success-text">Đã ở OFFER</span>;
    return <button className="secondary" onClick={() => stage.mutate({ id: String(row.id), stage: next })}>→ {next}</button>;
  }
  if (tab === 'offers' && String(row.status) === 'DRAFT') {
    return <div className="button-row"><button className="secondary" onClick={() => offer.mutate({ id: String(row.id), decision: 'ACCEPTED' })}>Nhận</button><button className="secondary" onClick={() => offer.mutate({ id: String(row.id), decision: 'DECLINED' })}>Từ chối</button></div>;
  }
  return null;
}

const positionSchema = z.object({ code: z.string().trim().min(1).max(50), title: z.string().trim().min(1).max(180), openings: z.number().int().min(1) });
const candidateSchema = z.object({ positionId: z.string().uuid(), fullName: z.string().trim().min(1).max(180), email: z.string().trim().email() });
const interviewSchema = z.object({ candidateId: z.string().uuid(), roundName: z.string().trim().min(1).max(100), scheduledAt: z.string().min(1), durationMinutes: z.number().int().min(15).max(480) });
const offerSchema = z.object({ candidateId: z.string().uuid(), proposedTitle: z.string().trim().min(1).max(180) });

function FormPanel({ tab, companyId }: { tab: Tab; companyId: string }) {
  const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['hr-recruitment', tab, companyId] });
  const position = useForm<z.infer<typeof positionSchema>>({ resolver: zodResolver(positionSchema), defaultValues: { code: '', title: '', openings: 1 } });
  const candidate = useForm<z.infer<typeof candidateSchema>>({ resolver: zodResolver(candidateSchema), defaultValues: { positionId: '', fullName: '', email: '' } });
  const interview = useForm<z.infer<typeof interviewSchema>>({ resolver: zodResolver(interviewSchema), defaultValues: { candidateId: '', roundName: '', scheduledAt: '', durationMinutes: 60 } });
  const offer = useForm<z.infer<typeof offerSchema>>({ resolver: zodResolver(offerSchema), defaultValues: { candidateId: '', proposedTitle: '' } });
  const create = (url: string, values: Record<string, unknown>) => api.post<Created>(url, values, { params: { companyId } });
  const positionMutation = useMutation({ mutationFn: (values: z.infer<typeof positionSchema>) => create('/hr/recruitment/positions', values), onSuccess: () => { position.reset(); invalidate(); } });
  const candidateMutation = useMutation({ mutationFn: (values: z.infer<typeof candidateSchema>) => create('/hr/recruitment/candidates', values), onSuccess: () => { candidate.reset(); invalidate(); } });
  const interviewMutation = useMutation({ mutationFn: (values: z.infer<typeof interviewSchema>) => create('/hr/recruitment/interviews', { ...values, scheduledAt: new Date(values.scheduledAt).toISOString() }), onSuccess: () => { interview.reset(); invalidate(); } });
  const offerMutation = useMutation({ mutationFn: (values: z.infer<typeof offerSchema>) => create('/hr/recruitment/offers', values), onSuccess: () => { offer.reset(); invalidate(); } });
  const panel = 'panel form-grid compact-form';
  if (tab === 'positions') return <form className={panel} onSubmit={position.handleSubmit(values => positionMutation.mutate(values))}><span className="form-section-title">Tạo vị trí tuyển dụng</span><label>Mã<input {...position.register('code')} /></label><label>Tên vị trí<input {...position.register('title')} /></label><label>Chấm số<input type="number" min={1} {...position.register('openings', { valueAsNumber: true })} /></label><div className="form-actions"><button className="primary" disabled={positionMutation.isPending}>Lưu vị trí</button>{positionMutation.error && <span className="form-error">{positionMutation.error.message}</span>}</div></form>;
  if (tab === 'candidates') return <form className={panel} onSubmit={candidate.handleSubmit(values => candidateMutation.mutate(values))}><span className="form-section-title">Thêm ứng viên</span><label>UUID vị trí<input {...candidate.register('positionId')} /></label><label>Họ tên<input {...candidate.register('fullName')} /></label><label>Email<input type="email" {...candidate.register('email')} /></label><div className="form-actions"><button className="primary" disabled={candidateMutation.isPending}>Lưu ứng viên</button>{candidateMutation.error && <span className="form-error">{candidateMutation.error.message}</span>}</div></form>;
  if (tab === 'interviews') return <form className={panel} onSubmit={interview.handleSubmit(values => interviewMutation.mutate(values))}><span className="form-section-title">Đặt lịch phỏng vấn</span><label>UUID ứng viên<input {...interview.register('candidateId')} /></label><label>Vòng<input {...interview.register('roundName')} /></label><label>Thời điểm<input type="datetime-local" {...interview.register('scheduledAt')} /></label><label>Phút (15–480)<input type="number" min={15} max={480} {...interview.register('durationMinutes', { valueAsNumber: true })} /></label><div className="form-actions"><button className="primary" disabled={interviewMutation.isPending}>Đặt lịch</button>{interviewMutation.error && <span className="form-error">{interviewMutation.error.message}</span>}</div></form>;
  return <form className={panel} onSubmit={offer.handleSubmit(values => offerMutation.mutate(values))}><span className="form-section-title">Gửi thư mời</span><label>UUID ứng viên<input {...offer.register('candidateId')} /></label><label>Chức danh<input {...offer.register('proposedTitle')} /></label><div className="form-actions"><button className="primary" disabled={offerMutation.isPending}>Lưu thư mời</button>{offerMutation.error && <span className="form-error">{offerMutation.error.message}</span>}</div></form>;
}
