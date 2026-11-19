insert into permission_catalog(code,module,description,sensitive) values
('reporting.access','REPORTING','Truy cập trung tâm điều hành và báo cáo',false),
('reporting.read','REPORTING','Xem KPI và báo cáo quản trị trong phạm vi được cấp',true),
('reporting.manage','REPORTING','Tạo snapshot và cấu hình báo cáo quản trị',true),
('reporting.export','REPORTING','Xuất dữ liệu báo cáo quản trị',true),
('strategy.read','REPORTING','Xem mục tiêu chiến lược và kết quả then chốt',false),
('strategy.write','REPORTING','Tạo và cập nhật mục tiêu chiến lược trong phạm vi được cấp',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r cross join permission_catalog p
where r.code='COMPANY_ADMIN' and p.module='REPORTING'
on conflict(role_id,permission_code) do nothing;

insert into company_roles(id,company_id,code,name,description,system_role,active)
select substring(md5(c.id || ':ROLE:EXECUTIVE_MANAGER'),1,8) || '-' || substring(md5(c.id || ':ROLE:EXECUTIVE_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':ROLE:EXECUTIVE_MANAGER'),13,4) || '-' || substring(md5(c.id || ':ROLE:EXECUTIVE_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':ROLE:EXECUTIVE_MANAGER'),21,12),c.id,'EXECUTIVE_MANAGER','Điều hành & Báo cáo',
       'Xem KPI toàn công ty, quản lý mục tiêu chiến lược và snapshot báo cáo',true,true
from companies c on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.module='REPORTING'
where r.code='EXECUTIVE_MANAGER'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
  ('reporting.access','reporting.read','strategy.read','strategy.write')
where r.code='MANAGER'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
  ('reporting.access','reporting.read','strategy.read')
where r.code='AUDITOR'
on conflict(role_id,permission_code) do nothing;

create table strategic_objectives (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null,
  title varchar(255) not null, description varchar(1500) null,
  scope_type varchar(20) not null, scope_id char(36) not null,
  owner_subject varchar(100) not null, start_date date not null, end_date date not null,
  status varchar(20) not null default 'DRAFT', progress_percent decimal(5,2) not null default 0,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_strategic_objective unique(company_id,code),
  constraint ck_strategic_objective_scope check(scope_type in ('COMPANY','DEPARTMENT','PROJECT')),
  constraint ck_strategic_objective_status check(status in ('DRAFT','ACTIVE','AT_RISK','COMPLETED','ARCHIVED')),
  constraint ck_strategic_objective_dates check(end_date>=start_date),
  constraint ck_strategic_objective_progress check(progress_percent between 0 and 100),
  constraint fk_strategic_objective_company foreign key(company_id) references companies(id) on delete cascade
);

create table strategic_key_results (
  id char(36) primary key, company_id char(36) not null, objective_id char(36) not null,
  code varchar(60) not null, title varchar(255) not null, metric_type varchar(20) not null,
  baseline_value decimal(19,4) not null default 0, target_value decimal(19,4) not null,
  current_value decimal(19,4) not null default 0, unit varchar(40) null,
  weight decimal(5,2) not null default 1, status varchar(20) not null default 'ON_TRACK',
  updated_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_strategic_key_result unique(objective_id,code),
  constraint ck_strategic_key_result_type check(metric_type in ('NUMBER','PERCENT','CURRENCY','BOOLEAN')),
  constraint ck_strategic_key_result_status check(status in ('ON_TRACK','AT_RISK','OFF_TRACK','COMPLETED')),
  constraint ck_strategic_key_result_weight check(weight>0 and weight<=100),
  constraint ck_strategic_key_result_target check(target_value<>baseline_value),
  constraint fk_strategic_key_result_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_strategic_key_result_objective foreign key(objective_id) references strategic_objectives(id) on delete cascade
);

create table management_report_snapshots (
  id char(36) primary key, company_id char(36) not null, name varchar(255) not null,
  report_type varchar(30) not null default 'EXECUTIVE', scope_type varchar(20) not null,
  scope_id char(36) not null, period_start date not null, period_end date not null,
  data_json jsonb not null, generated_by_subject varchar(100) not null,
  generated_at timestamp(6) not null default current_timestamp(6),
  constraint ck_management_report_type check(report_type in ('EXECUTIVE','FINANCE','PROJECT','HR','SALES','OPERATIONS')),
  constraint ck_management_report_scope check(scope_type in ('COMPANY','DEPARTMENT','PROJECT')),
  constraint ck_management_report_dates check(period_end>=period_start),
  constraint fk_management_report_company foreign key(company_id) references companies(id) on delete cascade
);

create index ix_strategic_objective_scope on strategic_objectives(company_id,scope_type,scope_id,status);
create index ix_strategic_key_result_objective on strategic_key_results(company_id,objective_id,status);
create index ix_management_report_period on management_report_snapshots(company_id,period_end,generated_at);
