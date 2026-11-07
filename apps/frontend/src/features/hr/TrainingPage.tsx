import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BookOpenCheck } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

type Row = Record<string, unknown>;
type Created = { id: string; status: string };
const courseSchema = z.object({ code: z.string().trim().min(1).max(50), title: z.string().trim().min(1).max(255), deliveryType: z.string().trim().min(1).max(30), passingScore: z.number().min(0).max(100).optional() });
const enrollmentSchema = z.object({ courseId: z.string().uuid(), employeeId: z.string().uuid() });
const certificateSchema = z.object({ employeeId: z.string().uuid(), name: z.string().trim().min(1).max(255), issuer: z.string().trim().max(180).optional(), issuedOn: z.string().optional(), expiresOn: z.string().optional() });
const skillSchema = z.object({ employeeId: z.string().uuid(), skillName: z.string().trim().min(1).max(180), proficiency: z.enum(['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT']) });
const progressSchema = z.object({ enrollmentId: z.string().uuid(), progressPercent: z.number().min(0).max(100), score: z.number().min(0).max(100).optional() });

export function TrainingPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [tab, setTab] = useState<'enrollments' | 'courses'>('enrollments');
  const enrollments = useQuery({ queryKey: ['hr-training', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/training', { params: { companyId } })).data });
  const courses = useQuery({ queryKey: ['hr-training-courses', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Row[]>('/hr/training/courses', { params: { companyId } })).data });
  const courseForm = useForm<z.infer<typeof courseSchema>>({ resolver: zodResolver(courseSchema), defaultValues: { code: '', title: '', deliveryType: 'ONLINE' } });
  const enrollmentForm = useForm<z.infer<typeof enrollmentSchema>>({ resolver: zodResolver(enrollmentSchema), defaultValues: { courseId: '', employeeId: '' } });
  const createCourse = useMutation({ mutationFn: async (values: z.infer<typeof courseSchema>) => (await api.post<Created>('/hr/training/courses', values, { params: { companyId } })).data,
    onSuccess: () => { courseForm.reset(); void queryClient.invalidateQueries({ queryKey: ['hr-training-courses', companyId] }); } });
  const enroll = useMutation({ mutationFn: async (values: z.infer<typeof enrollmentSchema>) => (await api.post<Created>('/hr/training/enrollments', values, { params: { companyId } })).data,
    onSuccess: () => { enrollmentForm.reset(); void queryClient.invalidateQueries({ queryKey: ['hr-training', companyId] }); } });
  return <><div className="page-heading"><span className="eyebrow">{company?.code || 'HR'} / training</span><h1>Đào tạo nhân viên</h1><p>Khóa học, bài kiểm tra, chứng chỉ, kỹ năng và tiến độ hoàn thành.</p></div>
    <div className="button-row"><button className={tab === 'enrollments' ? 'primary' : 'secondary'} onClick={() => setTab('enrollments')}>Tiến độ đào tạo</button><button className={tab === 'courses' ? 'primary' : 'secondary'} onClick={() => setTab('courses')}>Khóa học</button></div>
    <section className="panel table-panel"><h2>{tab === 'enrollments' ? 'Tiến độ của nhân viên' : 'Danh sách khóa học'}</h2>{tab === 'enrollments'
      ? (enrollments.data?.length ? <div className="table-scroll"><table><thead><tr><th>Nhân viên</th><th>Khóa học</th><th>Tiến độ</th><th>Điểm</th><th>Trạng thái</th></tr></thead><tbody>{enrollments.data.map(row => <tr key={String(row.id)}><td><strong>{String(row.full_name)}</strong><small>{String(row.employee_code)}</small></td><td>{String(row.course_title)}</td><td>{String(row.progress_percent)}%</td><td>{String(row.score ?? '—')}</td><td><span className="status-pill">{String(row.status)}</span></td></tr>)}</tbody></table></div> : <div className="empty-table"><BookOpenCheck /><p>Chưa có khóa đào tạo.</p></div>)
      : (courses.data?.length ? <div className="table-scroll"><table><thead><tr><th>Mã</th><th>Khóa học</th><th>Hình thức</th><th>Điểm đạt</th></tr></thead><tbody>{courses.data.map(row => <tr key={String(row.id)}><td>{String(row.code)}</td><td><strong>{String(row.title)}</strong><small>{String(row.description || '')}</small></td><td>{String(row.delivery_type)}</td><td>{String(row.passing_score ?? '—')}</td></tr>)}</tbody></table></div> : <div className="empty-table"><p>Chưa có khóa học. Tạo ở phía dưới.</p></div>)}</section>
    {can('hr.training.write') && tab === 'courses' && <form className="panel form-grid compact-form" onSubmit={courseForm.handleSubmit(values => createCourse.mutate(values))}><span className="form-section-title">Tạo khóa học</span><label>Mã<input {...courseForm.register('code')} /></label><label>Tên khóa<input {...courseForm.register('title')} /></label><label>Hình thức<input {...courseForm.register('deliveryType')} /></label><label>Điểm đạt<input type="number" min={0} max={100} {...courseForm.register('passingScore', { valueAsNumber: true })} /></label><div className="form-actions"><button className="primary" disabled={createCourse.isPending}>Lưu khóa</button>{createCourse.error && <span className="form-error">{createCourse.error.message}</span>}</div></form>}
    {can('hr.training.write') && <form className="panel form-grid compact-form" onSubmit={enrollmentForm.handleSubmit(values => enroll.mutate(values))}><span className="form-section-title">Giao khóa học cho nhân viên</span><label>UUID khóa học<input {...enrollmentForm.register('courseId')} /></label><label>UUID nhân viên<input {...enrollmentForm.register('employeeId')} /></label><div className="form-actions"><button className="primary" disabled={enroll.isPending}>Giao khóa</button>{enroll.error && <span className="form-error">{enroll.error.message}</span>}</div></form>}
    {can('hr.training.write') && <TrainingExtras companyId={companyId} />}
  </>;
}

function TrainingExtras({ companyId }: { companyId: string }) {
  const queryClient = useQueryClient();
  const invalidate = () => void queryClient.invalidateQueries({ queryKey: ['hr-training', companyId] });
  const progressForm = useForm<z.infer<typeof progressSchema>>({ resolver: zodResolver(progressSchema), defaultValues: { enrollmentId: '', progressPercent: 0 } });
  const certificateForm = useForm<z.infer<typeof certificateSchema>>({ resolver: zodResolver(certificateSchema), defaultValues: { employeeId: '', name: '' } });
  const skillForm = useForm<z.infer<typeof skillSchema>>({ resolver: zodResolver(skillSchema), defaultValues: { employeeId: '', skillName: '', proficiency: 'INTERMEDIATE' } });
  const progress = useMutation({ mutationFn: async (values: z.infer<typeof progressSchema>) => (await api.post<Created>(`/hr/training/enrollments/${values.enrollmentId}/progress`, { progressPercent: values.progressPercent, score: values.score }, { params: { companyId } })).data, onSuccess: invalidate });
  const certificate = useMutation({ mutationFn: async (values: z.infer<typeof certificateSchema>) => (await api.post<Created>('/hr/training/certificates', values, { params: { companyId } })).data,
    onSuccess: () => { certificateForm.reset(); invalidate(); } });
  const skill = useMutation({ mutationFn: async (values: z.infer<typeof skillSchema>) => (await api.post<Created>('/hr/training/skills', values, { params: { companyId } })).data,
    onSuccess: () => { skillForm.reset(); invalidate(); } });
  const panel = 'panel form-grid compact-form';
  return <>
    <form className={panel} onSubmit={progressForm.handleSubmit(values => progress.mutate(values))}><span className="form-section-title">Cập nhật tiến độ / điểm bài kiểm tra</span><label>UUID khóa giao<input {...progressForm.register('enrollmentId')} /></label><label>Tiến độ (%)<input type="number" min={0} max={100} {...progressForm.register('progressPercent', { valueAsNumber: true })} /></label><label>Điểm<input type="number" min={0} max={100} {...progressForm.register('score', { valueAsNumber: true })} /></label><div className="form-actions"><button className="primary" disabled={progress.isPending}>Lưu tiến độ</button>{progress.error && <span className="form-error">{progress.error.message}</span>}</div></form>
    <form className={panel} onSubmit={certificateForm.handleSubmit(values => certificate.mutate(values))}><span className="form-section-title">Ghi nhận chứng chỉ</span><label>UUID nhân viên<input {...certificateForm.register('employeeId')} /></label><label>Tên chứng chỉ<input {...certificateForm.register('name')} /></label><label>Đơn vị cấp<input {...certificateForm.register('issuer')} /></label><label>Cấp ngày<input type="date" {...certificateForm.register('issuedOn')} /></label><label>Hết hạn<input type="date" {...certificateForm.register('expiresOn')} /></label><div className="form-actions"><button className="primary" disabled={certificate.isPending}>Lưu chứng chỉ</button>{certificate.error && <span className="form-error">{certificate.error.message}</span>}</div></form>
    <form className={panel} onSubmit={skillForm.handleSubmit(values => skill.mutate(values))}><span className="form-section-title">Ghi nhận kỹ năng</span><label>UUID nhân viên<input {...skillForm.register('employeeId')} /></label><label>Kỹ năng<input {...skillForm.register('skillName')} /></label><label>Trình độ<select {...skillForm.register('proficiency')}>{['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><div className="form-actions"><button className="primary" disabled={skill.isPending}>Lưu kỹ năng</button>{skill.error && <span className="form-error">{skill.error.message}</span>}</div></form>
  </>;
}
