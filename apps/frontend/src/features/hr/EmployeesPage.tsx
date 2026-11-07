import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, Paperclip, Plus, UserRound } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useOutletContext } from 'react-router-dom';
import { z } from 'zod';
import type { Company, Employee, EmployeePrivateProfile } from '../../core/domain/types';
import { api } from '../../core/http/apiClient';
import { useAuth } from '../auth/AuthProvider';

const documentSchema = z.object({ employeeId: z.string().uuid(), documentType: z.string().trim().min(1).max(60),
  displayName: z.string().trim().min(1).max(255), objectKey: z.string().trim().min(1).max(500),
  classification: z.enum(['PUBLIC', 'INTERNAL', 'PRIVATE']) });
type DocumentRow = Record<string, unknown>;

const updateSchema = z.object({ employeeId: z.string().uuid(), fullName: z.string().trim().max(180).optional(),
  workEmail: z.string().trim().email().or(z.literal('')).optional(), jobTitle: z.string().trim().max(180).optional(),
  departmentId: z.string().trim().max(36).optional(), managerEmployeeId: z.string().trim().max(36).optional(),
  employmentStatus: z.string().trim().max(30).optional() });
const privateUpdateSchema = z.object({ employeeId: z.string().uuid(), dateOfBirth: z.string().optional(),
  identityType: z.string().trim().max(40).optional(), identityNumber: z.string().trim().max(255).optional(),
  personalEmail: z.string().trim().email().or(z.literal('')).optional(), personalPhone: z.string().trim().max(80).optional(),
  address: z.string().trim().max(1000).optional(), emergencyName: z.string().trim().max(180).optional(),
  emergencyPhone: z.string().trim().max(80).optional() });

const employeeSchema = z.object({
  employeeCode: z.string().trim().min(1).max(40), fullName: z.string().trim().min(1).max(180),
  workEmail: z.string().trim().email().or(z.literal('')), jobTitle: z.string().trim().min(1).max(180),
  joinDate: z.string().min(1), keycloakSubject: z.string().trim().max(100).optional(),
  dateOfBirth: z.string().optional(), identityType: z.string().trim().max(40).optional(), identityNumber: z.string().trim().max(255).optional(),
  personalEmail: z.string().trim().email().or(z.literal('')).optional(), personalPhone: z.string().trim().max(80).optional(),
  address: z.string().trim().max(1000).optional(), emergencyName: z.string().trim().max(180).optional(), emergencyPhone: z.string().trim().max(80).optional(),
});
type EmployeeForm = z.infer<typeof employeeSchema>;

export function EmployeesPage() {
  const { companyId, company } = useOutletContext<{ companyId: string; company?: Company }>();
  const { can } = useAuth(); const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false); const [privateEmployee, setPrivateEmployee] = useState<Employee | null>(null);
  const [documentEmployee, setDocumentEmployee] = useState<Employee | null>(null);
  const documents = useQuery({ queryKey: ['hr-employee-documents', companyId, documentEmployee?.id], enabled: !!documentEmployee && can('hr.employee.read.sensitive'),
    queryFn: async () => (await api.get<DocumentRow[]>(`/hr/employees/${documentEmployee!.id}/documents`, { params: { companyId } })).data });
  const docForm = useForm<z.infer<typeof documentSchema>>({ resolver: zodResolver(documentSchema), defaultValues: { employeeId: documentEmployee?.id || '', documentType: '', displayName: '', objectKey: '', classification: 'PRIVATE' } });
  const updateForm = useForm<z.infer<typeof updateSchema>>({ resolver: zodResolver(updateSchema), defaultValues: { employeeId: '' } });
  const privateUpdateForm = useForm<z.infer<typeof privateUpdateSchema>>({ resolver: zodResolver(privateUpdateSchema), defaultValues: { employeeId: '' } });
  const updateEmployee = useMutation({ mutationFn: async (values: z.infer<typeof updateSchema>) => {
      const clean = Object.fromEntries(Object.entries(values).filter(([, v]) => v !== undefined && v !== '')) as Record<string, unknown>;
      return (await api.patch<Employee>(`/hr/employees/${values.employeeId}`, clean, { params: { companyId } })).data;
    }, onSuccess: () => { updateForm.reset(); queryClient.invalidateQueries({ queryKey: ['hr-employees', companyId] }); } });
  const updatePrivate = useMutation({ mutationFn: async (values: z.infer<typeof privateUpdateSchema>) => {
      const clean = Object.fromEntries(Object.entries(values).filter(([, v]) => v !== undefined && v !== '')) as Record<string, unknown>;
      return (await api.patch<EmployeePrivateProfile>(`/hr/employees/${values.employeeId}/private`, clean, { params: { companyId } })).data;
    }, onSuccess: () => { privateUpdateForm.reset(); void queryClient.invalidateQueries({ queryKey: ['hr-employee-private', companyId] }); } });
  const addDocument = useMutation({ mutationFn: async (values: z.infer<typeof documentSchema>) => (await api.post<{ id: string; status: string }>('/hr/employees/documents', values, { params: { companyId } })).data,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['hr-employee-documents', companyId, documentEmployee?.id] }) });
  const employees = useQuery({ queryKey: ['hr-employees', companyId], enabled: !!companyId,
    queryFn: async () => (await api.get<Employee[]>('/hr/employees', { params: { companyId } })).data });
  const privateProfile = useQuery({ queryKey: ['hr-employee-private', companyId, privateEmployee?.id], enabled: !!privateEmployee && can('hr.employee.read.sensitive'),
    queryFn: async () => (await api.get<EmployeePrivateProfile>(`/hr/employees/${privateEmployee!.id}/private`, { params: { companyId } })).data });
  const form = useForm<EmployeeForm>({ resolver: zodResolver(employeeSchema), defaultValues: { employeeCode: '', fullName: '', workEmail: '', jobTitle: '', joinDate: '', keycloakSubject: '', dateOfBirth: '', identityType: '', identityNumber: '', personalEmail: '', personalPhone: '', address: '', emergencyName: '', emergencyPhone: '' } });
  const create = useMutation({ mutationFn: async (values: EmployeeForm) => { const { dateOfBirth, identityType, identityNumber, personalEmail, personalPhone, address, emergencyName, emergencyPhone, ...basic } = values; const privateProfile = can('hr.employee.read.sensitive') ? { dateOfBirth: dateOfBirth || null, identityType: identityType || null, identityNumber: identityNumber || null, personalEmail: personalEmail || null, personalPhone: personalPhone || null, address: address || null, emergencyName: emergencyName || null, emergencyPhone: emergencyPhone || null } : null; return (await api.post<Employee>('/hr/employees', { ...basic, workEmail: basic.workEmail || null, keycloakSubject: basic.keycloakSubject || null, privateProfile }, { params: { companyId } })).data; },
    onSuccess: async () => { form.reset(); setCreating(false); await queryClient.invalidateQueries({ queryKey: ['hr-employees', companyId] }); await queryClient.invalidateQueries({ queryKey: ['hr-summary', companyId] }); } });
  return <><div className="page-heading heading-actions"><div><span className="eyebrow">{company?.code || 'HR'} / employees</span><h1>Hồ sơ nhân viên</h1><p>Danh sách này chỉ chứa dữ liệu công việc cơ bản.</p></div>{can('hr.employee.write') && <button className="primary" onClick={() => setCreating(value => !value)}><Plus size={17} /> Thêm nhân viên</button>}</div>
    {creating && <form className="panel form-grid" onSubmit={form.handleSubmit(values => create.mutate(values))}><label>Mã nhân viên<input {...form.register('employeeCode')} /></label><label>Họ tên<input {...form.register('fullName')} /></label><label>Email công việc<input type="email" {...form.register('workEmail')} /></label><label>Chức danh<input {...form.register('jobTitle')} /></label><label>Ngày vào làm<input type="date" {...form.register('joinDate')} /></label><label>Keycloak subject<input {...form.register('keycloakSubject')} /></label>{can('hr.employee.read.sensitive') && <><div className="form-section-title">Thông tin riêng tư — được mã hóa</div><label>Ngày sinh<input type="date" {...form.register('dateOfBirth')} /></label><label>Loại định danh<input {...form.register('identityType')} /></label><label>Số định danh<input {...form.register('identityNumber')} /></label><label>Email cá nhân<input type="email" {...form.register('personalEmail')} /></label><label>Điện thoại cá nhân<input {...form.register('personalPhone')} /></label><label>Địa chỉ<input {...form.register('address')} /></label><label>Người liên hệ khẩn cấp<input {...form.register('emergencyName')} /></label><label>SĐT khẩn cấp<input {...form.register('emergencyPhone')} /></label></>}<div className="form-actions"><button className="primary" disabled={create.isPending}>Lưu hồ sơ</button>{create.error && <span className="form-error">{create.error.message}</span>}</div></form>}
    <section className="panel table-panel"><div className="table-scroll"><table><thead><tr><th>Mã</th><th>Nhân viên</th><th>Phòng ban</th><th>Chức danh</th><th>Ngày vào làm</th><th>Trạng thái</th>{can('hr.employee.read.sensitive') && <><th>Riêng tư</th><th>Hồ sơ đính kèm</th></>}</tr></thead><tbody>{employees.data?.map(employee => <tr key={employee.id}><td>{employee.employeeCode}</td><td><strong>{employee.fullName}</strong><small>{employee.workEmail || '—'}</small></td><td>{employee.departmentName || '—'}</td><td>{employee.jobTitle}</td><td>{employee.joinDate}</td><td><span className="status-pill">{employee.employmentStatus}</span></td>{can('hr.employee.read.sensitive') && <><td><button className="icon-button compact" aria-label="Xem dữ liệu riêng tư" onClick={() => { setPrivateEmployee(employee); setDocumentEmployee(null); }}><Eye size={16} /></button></td>
<td><button className="icon-button compact" aria-label="Xem hồ sơ đính kèm" onClick={() => { setDocumentEmployee(employee); setPrivateEmployee(null); }}><Paperclip size={16} /></button></td></>}</tr>)}</tbody></table></div>{!employees.isLoading && !employees.data?.length && <div className="empty-table"><UserRound /><p>Chưa có hồ sơ nhân viên.</p></div>}</section>
    {privateEmployee && <section className="panel sensitive-panel"><div className="heading-actions"><div><span className="kicker">Restricted data</span><h2>{privateEmployee.fullName}</h2></div><button className="icon-button compact" onClick={() => setPrivateEmployee(null)}>×</button></div>{privateProfile.isLoading ? <p>Đang tải…</p> : privateProfile.isError ? <p>Không có hồ sơ riêng tư hoặc bạn không có quyền.</p> : <dl className="private-grid"><div><dt>Ngày sinh</dt><dd>{privateProfile.data?.dateOfBirth || '—'}</dd></div><div><dt>Định danh</dt><dd>{privateProfile.data?.identityType || '—'} · {privateProfile.data?.identityNumber || '—'}</dd></div><div><dt>Liên hệ cá nhân</dt><dd>{privateProfile.data?.personalEmail || privateProfile.data?.personalPhone || '—'}</dd></div><div><dt>Liên hệ khẩn cấp</dt><dd>{privateProfile.data?.emergencyName || '—'} · {privateProfile.data?.emergencyPhone || '—'}</dd></div></dl>}</section>}
{can('hr.employee.write') && <section className="panel"><h2>Cập nhật hồ sơ cơ bản</h2><form className="form-grid compact-form" onSubmit={updateForm.handleSubmit(values => updateEmployee.mutate(values))}><span className="form-section-title">Giữ nguyên các trường bỏ trống</span><label>UUID nhân viên<input {...updateForm.register('employeeId')} /></label><label>Họ tên<input {...updateForm.register('fullName')} /></label><label>Email công việc<input type="email" {...updateForm.register('workEmail')} /></label><label>Chức danh<input {...updateForm.register('jobTitle')} /></label><label>UUID phòng ban<input {...updateForm.register('departmentId')} /></label><label>UUID quản lý<input {...updateForm.register('managerEmployeeId')} /></label><label>Trạng thái<input {...updateForm.register('employmentStatus')} /></label><div className="form-actions"><button className="primary" disabled={updateEmployee.isPending}>Cập nhật</button>{updateEmployee.error && <span className="form-error">{updateEmployee.error.message}</span>}</div></form></section>}
{can('hr.employee.write') && can('hr.employee.read.sensitive') && <section className="panel"><h2>Cập nhật dữ liệu riêng tư (mã hóa)</h2><form className="form-grid compact-form" onSubmit={privateUpdateForm.handleSubmit(values => updatePrivate.mutate(values))}><label>UUID nhân viên<input {...privateUpdateForm.register('employeeId')} /></label><label>Ngày sinh<input type="date" {...privateUpdateForm.register('dateOfBirth')} /></label><label>Loại định danh<input {...privateUpdateForm.register('identityType')} /></label><label>Số định danh<input {...privateUpdateForm.register('identityNumber')} /></label><label>Địa chỉ<input {...privateUpdateForm.register('address')} /></label><label>Liên hệ khẩn cấp<input {...privateUpdateForm.register('emergencyName')} /></label><label>SĐT khẩn cấp<input {...privateUpdateForm.register('emergencyPhone')} /></label><div className="form-actions"><button className="primary" disabled={updatePrivate.isPending}>Cập nhật</button>{updatePrivate.error && <span className="form-error">{updatePrivate.error.message}</span>}</div></form></section>}
    {documentEmployee && <section className="panel sensitive-panel"><div className="heading-actions"><div><span className="kicker">Hồ sơ đính kèm</span><h2>{documentEmployee.fullName}</h2></div><button className="icon-button compact" onClick={() => setDocumentEmployee(null)}>×</button></div>
      {documents.data?.length ? <div className="table-scroll"><table><thead><tr><th>Loại</th><th>Tên</th><th>Phân loại</th><th>Quét malware</th><th>Người tải</th></tr></thead><tbody>{documents.data.map(doc => <tr key={String(doc.id)}><td>{String(doc.document_type)}</td><td><strong>{String(doc.display_name)}</strong></td><td><span className="status-pill">{String(doc.classification)}</span></td><td>{String(doc.malware_scan_status)}</td><td>{String(doc.uploaded_by_subject)}</td></tr>)}</tbody></table></div> : <p className="table-message">Chưa có hồ sơ đính kèm.</p>}
      {can('hr.employee.write') && <form className="form-grid compact-form" onSubmit={docForm.handleSubmit(values => addDocument.mutate(values))}><span className="form-section-title">Ghi nhận hồ sơ đính kèm (object key)</span><label>Loại<input {...docForm.register('documentType')} /></label><label>Tên hiển thị<input {...docForm.register('displayName')} /></label><label>Object key<input {...docForm.register('objectKey')} /></label><label>Phân loại<select {...docForm.register('classification')}>{['PUBLIC', 'INTERNAL', 'PRIVATE'].map(item => <option key={item} value={item}>{item}</option>)}</select></label><div className="form-actions"><button className="primary" disabled={addDocument.isPending}>Ghi nhận</button>{addDocument.error && <span className="form-error">{addDocument.error.message}</span>}</div></form>}</section>}
  </>;
}
