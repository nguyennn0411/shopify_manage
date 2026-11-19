insert into permission_catalog(code,module,description,sensitive)
values ('hr.employee.write.sensitive','HR','Cập nhật dữ liệu nhân sự nhạy cảm',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,'hr.employee.write.sensitive'
from company_roles r
where r.code='COMPANY_ADMIN'
on conflict do nothing;

insert into company_roles(id,company_id,code,name,description,system_role)
select substring(md5(c.id || ':' || r.code),1,8) || '-' || substring(md5(c.id || ':' || r.code),9,4) || '-' ||
       substring(md5(c.id || ':' || r.code),13,4) || '-' || substring(md5(c.id || ':' || r.code),17,4) || '-' ||
       substring(md5(c.id || ':' || r.code),21,12),
       c.id,r.code,r.name,r.description,true
from companies c cross join (values
  ('HR_MANAGER','Quản lý nhân sự','Quản lý vòng đời nhân viên; không mặc định xem hoặc vận hành bảng lương'),
  ('PAYROLL_MANAGER','Quản lý tiền lương','Quản lý cấu trúc lương, kỳ lương và phiếu lương')
) as r(code,name,description)
on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','hr.access','hr.employee.read.basic','hr.employee.read.sensitive','hr.employee.write',
  'hr.employee.write.sensitive','hr.recruitment.read','hr.recruitment.write','hr.onboarding.read',
  'hr.onboarding.write','hr.attendance.read','hr.attendance.write','hr.attendance.approve',
  'hr.leave.read','hr.leave.write','hr.leave.approve','hr.performance.read','hr.performance.write',
  'hr.training.read','hr.training.write','hr.offboarding.read','hr.offboarding.write'
)
where r.code='HR_MANAGER'
on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','hr.access','hr.employee.read.basic','hr.payroll.read.all','hr.payroll.write'
)
where r.code='PAYROLL_MANAGER'
on conflict do nothing;

create index if not exists ix_employee_company_department on employees(company_id,department_id);
create index if not exists ix_employee_company_manager on employees(company_id,manager_employee_id);
create index if not exists ix_employee_company_status on employees(company_id,employment_status);

update employees set employment_status='TERMINATED'
where employment_status in ('INACTIVE','ARCHIVED');

alter table employees add constraint ck_employee_employment_status
  check(employment_status in ('ACTIVE','PROBATION','ON_LEAVE','SUSPENDED','OFFBOARDING','TERMINATED'));
