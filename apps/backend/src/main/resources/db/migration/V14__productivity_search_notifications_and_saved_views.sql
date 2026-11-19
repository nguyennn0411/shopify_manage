insert into permission_catalog(code,module,description,sensitive) values
('platform.search','PRODUCTIVITY','Tìm kiếm dữ liệu được phép truy cập trong công ty',false),
('notification.read','PRODUCTIVITY','Xem thông báo cá nhân',false),
('notification.write','PRODUCTIVITY','Tạo nhắc việc cho bản thân',false),
('notification.manage','PRODUCTIVITY','Gửi và quản trị thông báo cho người khác',true),
('saved-view.read','PRODUCTIVITY','Xem bộ lọc và cách hiển thị đã lưu',false),
('saved-view.write','PRODUCTIVITY','Tạo và cập nhật bộ lọc cá nhân',false),
('saved-view.share','PRODUCTIVITY','Chia sẻ bộ lọc cho công ty',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r cross join permission_catalog p
where r.code='COMPANY_ADMIN' and p.module='PRODUCTIVITY'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
('platform.search','notification.read','notification.write','saved-view.read','saved-view.write')
where r.code in ('MANAGER','EMPLOYEE')
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in
('platform.search','notification.read','saved-view.read')
where r.code='AUDITOR'
on conflict(role_id,permission_code) do nothing;

create table platform_notifications (
  id char(36) primary key, company_id char(36) not null, target_subject varchar(100) not null,
  title varchar(255) not null, body varchar(2000) null, category varchar(50) not null default 'GENERAL',
  severity varchar(20) not null default 'INFO', action_path varchar(500) null,
  source_type varchar(60) null, source_id varchar(100) null, dedupe_key varchar(180) null,
  created_by_subject varchar(100) not null, read_at timestamp(6) null, archived_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint ck_platform_notification_severity check(severity in ('INFO','SUCCESS','WARNING','CRITICAL')),
  constraint uq_platform_notification_dedupe unique(company_id,target_subject,dedupe_key),
  constraint fk_platform_notification_company foreign key(company_id) references companies(id) on delete cascade
);

create table platform_notification_preferences (
  id char(36) primary key, company_id char(36) not null, subject varchar(100) not null,
  category varchar(50) not null default 'ALL', in_app_enabled boolean not null default true,
  email_enabled boolean not null default false, quiet_start time null, quiet_end time null,
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_platform_notification_preference unique(company_id,subject,category),
  constraint fk_platform_notification_preference_company foreign key(company_id) references companies(id) on delete cascade
);

create table platform_saved_views (
  id char(36) primary key, company_id char(36) not null, owner_subject varchar(100) not null,
  module_key varchar(80) not null, name varchar(180) not null, config_json jsonb not null,
  shared boolean not null default false, default_view boolean not null default false,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_platform_saved_view_name unique(company_id,owner_subject,module_key,name),
  constraint fk_platform_saved_view_company foreign key(company_id) references companies(id) on delete cascade
);

create index ix_platform_notification_inbox on platform_notifications(company_id,target_subject,archived_at,read_at,created_at);
create index ix_platform_saved_view_lookup on platform_saved_views(company_id,module_key,owner_subject,shared);
