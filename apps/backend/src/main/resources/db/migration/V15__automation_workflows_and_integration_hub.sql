insert into permission_catalog(code,module,description,sensitive) values
('automation.access','AUTOMATION','Truy cập trung tâm tự động hóa',false),
('automation.read','AUTOMATION','Xem workflow và lịch sử chạy',false),
('automation.write','AUTOMATION','Thiết kế và kích hoạt workflow',true),
('automation.run','AUTOMATION','Chạy workflow thủ công',true),
('integration.read','AUTOMATION','Xem kết nối tích hợp',true),
('integration.write','AUTOMATION','Tạo và thay đổi kết nối tích hợp',true),
('webhook.read','AUTOMATION','Xem endpoint và lần gửi webhook',true),
('webhook.write','AUTOMATION','Tạo endpoint và yêu cầu gửi lại webhook',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r cross join permission_catalog p
where r.code='COMPANY_ADMIN' and p.module='AUTOMATION'
on conflict(role_id,permission_code) do nothing;

insert into company_roles(id,company_id,code,name,description,system_role,active)
select substring(md5(c.id || ':ROLE:AUTOMATION_MANAGER'),1,8) || '-' || substring(md5(c.id || ':ROLE:AUTOMATION_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':ROLE:AUTOMATION_MANAGER'),13,4) || '-' || substring(md5(c.id || ':ROLE:AUTOMATION_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':ROLE:AUTOMATION_MANAGER'),21,12),c.id,'AUTOMATION_MANAGER','Quản lý tự động hóa & tích hợp',
       'Thiết kế workflow, quản lý kết nối và webhook; không lưu bí mật tích hợp trực tiếp',true,true
from companies c on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.module='AUTOMATION'
where r.code='AUTOMATION_MANAGER'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in ('automation.access','automation.read','automation.run')
where r.code='MANAGER'
on conflict(role_id,permission_code) do nothing;

create table integration_connections (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(180) not null,
  connector_type varchar(40) not null, base_url varchar(500) null, credential_reference varchar(500) null,
  config_json jsonb not null default '{}'::jsonb, status varchar(20) not null default 'DRAFT',
  last_checked_at timestamp(6) null, last_success_at timestamp(6) null, last_error varchar(1000) null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_integration_connection unique(company_id,code),
  constraint ck_integration_connection_type check(connector_type in ('EMAIL','CALENDAR','ACCOUNTING','CRM','HR','PAYMENT','CUSTOM')),
  constraint ck_integration_connection_status check(status in ('DRAFT','ACTIVE','DISABLED','ERROR')),
  constraint fk_integration_connection_company foreign key(company_id) references companies(id) on delete cascade
);

create table integration_webhook_endpoints (
  id char(36) primary key, company_id char(36) not null, connection_id char(36) null,
  code varchar(60) not null, name varchar(180) not null, direction varchar(20) not null default 'OUTBOUND',
  event_type varchar(100) not null, target_url varchar(1000) not null, enabled boolean not null default true,
  timeout_seconds int not null default 10, max_attempts int not null default 5,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_integration_webhook unique(company_id,code),
  constraint ck_integration_webhook_direction check(direction='OUTBOUND'),
  constraint ck_integration_webhook_timeout check(timeout_seconds between 1 and 30),
  constraint ck_integration_webhook_attempts check(max_attempts between 1 and 10),
  constraint fk_integration_webhook_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_integration_webhook_connection foreign key(connection_id) references integration_connections(id) on delete set null
);

create table automation_workflows (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(180) not null,
  description varchar(1000) null, trigger_type varchar(20) not null default 'MANUAL',
  trigger_config jsonb not null default '{}'::jsonb, status varchar(20) not null default 'DRAFT',
  version int not null default 1, next_run_at timestamp(6) null, last_run_at timestamp(6) null,
  owner_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_automation_workflow unique(company_id,code),
  constraint ck_automation_trigger check(trigger_type in ('MANUAL','SCHEDULE','EVENT')),
  constraint ck_automation_workflow_status check(status in ('DRAFT','ACTIVE','PAUSED','ARCHIVED')),
  constraint fk_automation_workflow_company foreign key(company_id) references companies(id) on delete cascade
);

create table automation_steps (
  id char(36) primary key, company_id char(36) not null, workflow_id char(36) not null,
  step_order int not null, name varchar(180) not null, step_type varchar(30) not null,
  config_json jsonb not null, continue_on_error boolean not null default false,
  constraint uq_automation_step unique(workflow_id,step_order),
  constraint ck_automation_step_order check(step_order>0),
  constraint ck_automation_step_type check(step_type in ('NOTIFICATION','WEBHOOK','APPROVAL')),
  constraint fk_automation_step_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_automation_step_workflow foreign key(workflow_id) references automation_workflows(id) on delete cascade
);

create table automation_runs (
  id char(36) primary key, company_id char(36) not null, workflow_id char(36) not null,
  trigger_source varchar(30) not null, status varchar(20) not null default 'RUNNING',
  input_json jsonb not null default '{}'::jsonb, output_json jsonb null, error_message varchar(2000) null,
  started_by_subject varchar(100) not null, started_at timestamp(6) not null default current_timestamp(6),
  completed_at timestamp(6) null,
  constraint ck_automation_run_status check(status in ('RUNNING','SUCCEEDED','PARTIAL','FAILED','CANCELLED')),
  constraint fk_automation_run_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_automation_run_workflow foreign key(workflow_id) references automation_workflows(id) on delete cascade
);

create table automation_run_steps (
  id char(36) primary key, company_id char(36) not null, run_id char(36) not null, step_id char(36) not null,
  step_order int not null, status varchar(20) not null, output_json jsonb null, error_message varchar(2000) null,
  started_at timestamp(6) not null default current_timestamp(6), completed_at timestamp(6) null,
  constraint ck_automation_run_step_status check(status in ('RUNNING','SUCCEEDED','FAILED','SKIPPED')),
  constraint fk_automation_run_step_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_automation_run_step_run foreign key(run_id) references automation_runs(id) on delete cascade,
  constraint fk_automation_run_step_step foreign key(step_id) references automation_steps(id) on delete cascade
);

create table integration_webhook_deliveries (
  id char(36) primary key, company_id char(36) not null, endpoint_id char(36) not null,
  automation_run_id char(36) null, event_type varchar(100) not null, payload_json jsonb not null,
  status varchar(20) not null default 'PENDING', attempt_count int not null default 0,
  next_attempt_at timestamp(6) not null default current_timestamp(6), last_attempt_at timestamp(6) null,
  response_status int null, response_body varchar(2000) null, last_error varchar(2000) null,
  created_at timestamp(6) not null default current_timestamp(6), delivered_at timestamp(6) null,
  constraint ck_webhook_delivery_status check(status in ('PENDING','DELIVERED','FAILED','DEAD_LETTER')),
  constraint fk_webhook_delivery_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_webhook_delivery_endpoint foreign key(endpoint_id) references integration_webhook_endpoints(id) on delete cascade,
  constraint fk_webhook_delivery_run foreign key(automation_run_id) references automation_runs(id) on delete set null
);

create index ix_automation_schedule on automation_workflows(company_id,status,trigger_type,next_run_at);
create index ix_automation_runs on automation_runs(company_id,workflow_id,started_at);
create index ix_webhook_delivery_queue on integration_webhook_deliveries(status,next_attempt_at,attempt_count);
