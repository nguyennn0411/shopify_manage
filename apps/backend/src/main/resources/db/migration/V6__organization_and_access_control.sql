alter table companies
  add column legal_name varchar(255) null,
  add column brand_name varchar(180) null,
  add column tax_code varchar(80) null,
  add column registration_number varchar(100) null,
  add column email varchar(254) null,
  add column phone varchar(80) null,
  add column website varchar(500) null,
  add column address text null,
  add column logo_object_key varchar(500) null,
  add column bank_name varchar(180) null,
  add column bank_account_name varchar(180) null,
  add column bank_account_number varchar(120) null;

create unique index uq_company_tax_code on companies(tax_code) where tax_code is not null;

create table company_branches (
  id char(36) primary key,
  company_id char(36) not null,
  code varchar(50) not null,
  name varchar(180) not null,
  address text null,
  timezone varchar(80) not null default 'Asia/Ho_Chi_Minh',
  phone varchar(80) null,
  email varchar(254) null,
  manager_employee_id char(36) null,
  status varchar(20) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_company_branch unique(company_id, code),
  constraint fk_branch_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_branch_manager foreign key(manager_employee_id) references employees(id) on delete set null
);
create index ix_branch_company_status on company_branches(company_id, status);

alter table departments
  add column branch_id char(36) null,
  add column parent_department_id char(36) null,
  add column manager_employee_id char(36) null,
  add column description text null,
  add column status varchar(20) not null default 'ACTIVE',
  add column created_at timestamp(6) not null default current_timestamp(6),
  add column updated_at timestamp(6) not null default current_timestamp(6),
  add constraint fk_department_branch foreign key(branch_id) references company_branches(id) on delete set null,
  add constraint fk_department_parent foreign key(parent_department_id) references departments(id) on delete set null,
  add constraint fk_department_manager foreign key(manager_employee_id) references employees(id) on delete set null;
create index ix_department_company_parent on departments(company_id, parent_department_id);
create index ix_department_company_branch on departments(company_id, branch_id);

create table job_positions (
  id char(36) primary key,
  company_id char(36) not null,
  department_id char(36) null,
  code varchar(50) not null,
  title varchar(180) not null,
  level_name varchar(80) null,
  reports_to_position_id char(36) null,
  description text null,
  status varchar(20) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_job_position unique(company_id, code),
  constraint fk_job_position_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_job_position_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_job_position_parent foreign key(reports_to_position_id) references job_positions(id) on delete set null
);
create index ix_job_position_company_department on job_positions(company_id, department_id);

alter table employees
  add column position_id char(36) null,
  add column branch_id char(36) null,
  add constraint fk_employee_position foreign key(position_id) references job_positions(id) on delete set null,
  add constraint fk_employee_branch foreign key(branch_id) references company_branches(id) on delete set null;
create index ix_employee_company_position on employees(company_id, position_id);
create index ix_employee_company_branch on employees(company_id, branch_id);

alter table company_memberships
  add column status varchar(20) not null default 'ACTIVE',
  add column invited_at timestamp(6) null,
  add column activated_at timestamp(6) null,
  add column disabled_at timestamp(6) null;
update company_memberships set activated_at=created_at where activated_at is null;
create index ix_membership_company_status on company_memberships(company_id, status);

create table permission_catalog (
  code varchar(120) primary key,
  module varchar(60) not null,
  description varchar(255) not null,
  sensitive boolean not null default false
);

insert into permission_catalog(code,module,description,sensitive) values
('company.read','ORGANIZATION','Xem hồ sơ và cơ cấu công ty',false),
('company.write','ORGANIZATION','Cập nhật hồ sơ và cơ cấu công ty',true),
('identity.account.read','IDENTITY','Xem tài khoản thành viên',true),
('identity.account.write','IDENTITY','Mời, kích hoạt hoặc vô hiệu hóa thành viên',true),
('identity.role.read','IDENTITY','Xem vai trò và quyền',true),
('identity.role.write','IDENTITY','Tạo vai trò và gán quyền/phạm vi',true),
('hr.access','HR','Truy cập phân hệ nhân sự',false),
('hr.employee.read.basic','HR','Xem hồ sơ nhân viên cơ bản',false),
('hr.employee.read.sensitive','HR','Xem dữ liệu nhân sự nhạy cảm',true),
('hr.employee.write','HR','Cập nhật hồ sơ nhân viên',true),
('hr.recruitment.read','HR','Xem tuyển dụng',true),
('hr.recruitment.write','HR','Quản lý tuyển dụng',true),
('hr.onboarding.read','HR','Xem onboarding',false),
('hr.onboarding.write','HR','Quản lý onboarding',true),
('hr.attendance.read','HR','Xem chấm công',false),
('hr.attendance.write','HR','Ghi nhận chấm công',false),
('hr.attendance.approve','HR','Duyệt điều chỉnh công',true),
('hr.leave.read','HR','Xem nghỉ phép',false),
('hr.leave.write','HR','Tạo và cập nhật đơn nghỉ',false),
('hr.leave.approve','HR','Duyệt nghỉ phép',true),
('hr.payroll.read.own','HR','Xem phiếu lương cá nhân',true),
('hr.payroll.read.all','HR','Xem toàn bộ dữ liệu lương',true),
('hr.payroll.write','HR','Quản lý bảng lương',true),
('hr.performance.read','HR','Xem đánh giá hiệu suất',true),
('hr.performance.write','HR','Quản lý đánh giá hiệu suất',true),
('hr.training.read','HR','Xem đào tạo',false),
('hr.training.write','HR','Quản lý đào tạo',true),
('hr.offboarding.read','HR','Xem offboarding',true),
('hr.offboarding.write','HR','Quản lý offboarding',true),
('pm.access','PROJECT','Truy cập dự án và công việc',false),
('pm.project.read','PROJECT','Xem dự án',false),
('pm.project.write','PROJECT','Quản lý dự án',true),
('pm.task.read','PROJECT','Xem công việc',false),
('pm.task.write','PROJECT','Quản lý công việc',false),
('pm.sprint.read','PROJECT','Xem sprint',false),
('pm.sprint.write','PROJECT','Quản lý sprint',true),
('pm.workflow.manage','PROJECT','Cấu hình workflow',true),
('pm.timesheet.read','PROJECT','Xem timesheet',true),
('pm.timesheet.read.own','PROJECT','Xem timesheet cá nhân',false),
('pm.timesheet.write','PROJECT','Ghi timesheet',false),
('pm.timesheet.approve','PROJECT','Duyệt timesheet',true),
('pm.workload.read','PROJECT','Xem workload',true),
('pm.report.read','PROJECT','Xem báo cáo dự án',true),
('pm.issue.read','PROJECT','Xem sự cố',false),
('pm.issue.write','PROJECT','Quản lý sự cố',false),
('stores.read','COMMERCE','Xem cửa hàng',false),
('stores.write','COMMERCE','Quản lý cửa hàng',true),
('sales.read','COMMERCE','Xem bán hàng',false),
('sales.write','COMMERCE','Quản lý bán hàng',true),
('inventory.read','OPERATIONS','Xem tồn kho',false),
('inventory.write','OPERATIONS','Quản lý tồn kho',true),
('supplier.read','OPERATIONS','Xem nhà cung cấp',false),
('supplier.write','OPERATIONS','Quản lý nhà cung cấp',true),
('procurement.read','OPERATIONS','Xem mua sắm',false),
('procurement.write','OPERATIONS','Quản lý mua sắm',true),
('pim.read','OPERATIONS','Xem sản phẩm',false),
('pim.write','OPERATIONS','Quản lý sản phẩm',true),
('fulfillment.read','OPERATIONS','Xem giao nhận',false),
('fulfillment.write','OPERATIONS','Quản lý giao nhận',true),
('finance.read','FINANCE','Xem tài chính',true),
('finance.write','FINANCE','Quản lý tài chính',true),
('analytics.read','REPORTING','Xem phân tích',true),
('audit.read','PLATFORM','Xem nhật ký kiểm toán',true),
('platform.metrics.read','PLATFORM','Xem metrics vận hành',true);

create table company_roles (
  id char(36) primary key,
  company_id char(36) not null,
  code varchar(60) not null,
  name varchar(120) not null,
  description varchar(500) null,
  system_role boolean not null default false,
  active boolean not null default true,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_company_role unique(company_id, code),
  constraint fk_role_company foreign key(company_id) references companies(id) on delete cascade
);

create table company_role_permissions (
  role_id char(36) not null,
  permission_code varchar(120) not null,
  primary key(role_id, permission_code),
  constraint fk_role_permission_role foreign key(role_id) references company_roles(id) on delete cascade,
  constraint fk_role_permission_catalog foreign key(permission_code) references permission_catalog(code)
);

create table company_membership_roles (
  company_id char(36) not null,
  keycloak_subject varchar(100) not null,
  role_id char(36) not null,
  assigned_at timestamp(6) not null default current_timestamp(6),
  primary key(company_id, keycloak_subject, role_id),
  constraint fk_membership_role_membership foreign key(company_id, keycloak_subject)
    references company_memberships(company_id, keycloak_subject) on delete cascade,
  constraint fk_membership_role_role foreign key(role_id) references company_roles(id) on delete cascade
);

create table company_membership_scopes (
  company_id char(36) not null,
  keycloak_subject varchar(100) not null,
  scope_type varchar(30) not null,
  scope_id char(36) not null,
  assigned_at timestamp(6) not null default current_timestamp(6),
  primary key(company_id, keycloak_subject, scope_type, scope_id),
  constraint ck_membership_scope_type check(scope_type in ('SELF','TEAM','DEPARTMENT','BRANCH','PROJECT','COMPANY')),
  constraint fk_membership_scope_membership foreign key(company_id, keycloak_subject)
    references company_memberships(company_id, keycloak_subject) on delete cascade
);
create index ix_membership_scope_lookup on company_membership_scopes(company_id, keycloak_subject, scope_type);

insert into company_roles(id,company_id,code,name,description,system_role)
select substring(md5(c.id || ':' || r.code),1,8) || '-' || substring(md5(c.id || ':' || r.code),9,4) || '-' ||
       substring(md5(c.id || ':' || r.code),13,4) || '-' || substring(md5(c.id || ':' || r.code),17,4) || '-' ||
       substring(md5(c.id || ':' || r.code),21,12),
       c.id,r.code,r.name,r.description,true
from companies c cross join (values
  ('COMPANY_ADMIN','Quản trị công ty','Toàn quyền cấu hình trong công ty'),
  ('MANAGER','Quản lý','Quản lý tổ chức, nhân sự cơ bản và dự án trong phạm vi được cấp'),
  ('EMPLOYEE','Nhân viên','Truy cập cổng nhân viên và công việc được chia sẻ'),
  ('AUDITOR','Kiểm toán viên','Quyền đọc hồ sơ tổ chức và nhật ký được ủy quyền')
) as r(code,name,description)
on conflict(company_id,code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on r.code='COMPANY_ADMIN'
on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
  ('company.read','hr.access','hr.employee.read.basic','pm.access','pm.project.read','pm.project.write','pm.task.read','pm.task.write')
where r.code='MANAGER' on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
  ('company.read','hr.access','pm.access','pm.project.read','pm.task.read','pm.task.write')
where r.code='EMPLOYEE' on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in ('company.read','audit.read')
where r.code='AUDITOR' on conflict do nothing;
