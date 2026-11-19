insert into permission_catalog(code,module,description,sensitive) values
('workplace.access','WORKPLACE','Truy cập không gian làm việc nội bộ',false),
('document.read','WORKPLACE','Xem tài liệu được chia sẻ',false),
('document.write','WORKPLACE','Tạo và cập nhật tài liệu',true),
('document.share','WORKPLACE','Chia sẻ và thay đổi phạm vi tài liệu',true),
('knowledge.read','WORKPLACE','Xem kho tri thức',false),
('knowledge.write','WORKPLACE','Biên soạn và xuất bản kho tri thức',true),
('approval.read','WORKPLACE','Xem yêu cầu phê duyệt liên quan',false),
('approval.write','WORKPLACE','Tạo yêu cầu phê duyệt',true),
('approval.decide','WORKPLACE','Phê duyệt hoặc từ chối yêu cầu',true),
('form.read','WORKPLACE','Xem biểu mẫu nội bộ',false),
('form.write','WORKPLACE','Thiết kế và phát hành biểu mẫu',true),
('form.submit','WORKPLACE','Gửi biểu mẫu nội bộ',false),
('signature.read','WORKPLACE','Xem yêu cầu ký liên quan',false),
('signature.write','WORKPLACE','Tạo yêu cầu ký nội bộ',true),
('signature.sign','WORKPLACE','Ký xác nhận yêu cầu được giao',true),
('helpdesk.read','WORKPLACE','Xem yêu cầu hỗ trợ liên quan',false),
('helpdesk.write','WORKPLACE','Tạo và trao đổi yêu cầu hỗ trợ',false),
('helpdesk.manage','WORKPLACE','Phân công và xử lý yêu cầu hỗ trợ',true),
('communication.read','WORKPLACE','Xem kênh trao đổi được cấp',false),
('communication.write','WORKPLACE','Gửi nội dung vào kênh được cấp',false),
('communication.moderate','WORKPLACE','Quản trị kênh trao đổi nội bộ',true),
('legal.read','WORKPLACE','Xem hồ sơ pháp lý',true),
('legal.write','WORKPLACE','Quản lý hồ sơ và nghĩa vụ pháp lý',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r cross join permission_catalog p
where r.code='COMPANY_ADMIN' and p.module='WORKPLACE'
on conflict(role_id,permission_code) do nothing;

insert into company_roles(id,company_id,code,name,description,system_role,active)
select substring(md5(c.id || ':ROLE:WORKPLACE_MANAGER'),1,8) || '-' || substring(md5(c.id || ':ROLE:WORKPLACE_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':ROLE:WORKPLACE_MANAGER'),13,4) || '-' || substring(md5(c.id || ':ROLE:WORKPLACE_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':ROLE:WORKPLACE_MANAGER'),21,12),c.id,'WORKPLACE_MANAGER','Quản lý không gian làm việc',
       'Quản lý tài liệu, tri thức, biểu mẫu, helpdesk, truyền thông và hồ sơ pháp lý; quyền phê duyệt được cấp riêng',true,true
from companies c on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','workplace.access','document.read','document.write','document.share','knowledge.read','knowledge.write',
  'approval.read','approval.write','form.read','form.write','form.submit','signature.read','signature.write','signature.sign',
  'helpdesk.read','helpdesk.write','helpdesk.manage','communication.read','communication.write','communication.moderate',
  'legal.read','legal.write') where r.code='WORKPLACE_MANAGER'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'workplace.access','document.read','knowledge.read','approval.read','approval.write','form.read','form.submit',
  'signature.read','signature.sign','helpdesk.read','helpdesk.write','communication.read','communication.write')
where r.code='EMPLOYEE'
on conflict(role_id,permission_code) do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'workplace.access','document.read','knowledge.read','approval.read','approval.write','approval.decide','form.read','form.submit',
  'signature.read','signature.sign','helpdesk.read','helpdesk.write','communication.read','communication.write')
where r.code='MANAGER'
on conflict(role_id,permission_code) do nothing;

create table workplace_folders (
  id char(36) primary key, company_id char(36) not null, parent_folder_id char(36) null,
  name varchar(180) not null, description varchar(500) null, scope_type varchar(20) not null default 'COMPANY',
  scope_id char(36) null, owner_subject varchar(100) not null, archived boolean not null default false,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_folder unique(company_id,parent_folder_id,name),
  constraint ck_workplace_folder_scope check(scope_type in ('PRIVATE','DEPARTMENT','PROJECT','COMPANY')),
  constraint fk_workplace_folder_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_folder_parent foreign key(parent_folder_id) references workplace_folders(id) on delete cascade
);

create table workplace_documents (
  id char(36) primary key, company_id char(36) not null, folder_id char(36) null,
  title varchar(255) not null, category varchar(80) null, description varchar(1000) null,
  scope_type varchar(20) not null default 'COMPANY', scope_id char(36) null,
  status varchar(20) not null default 'DRAFT', current_version int not null default 0,
  owner_subject varchar(100) not null, archived_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint ck_workplace_document_scope check(scope_type in ('PRIVATE','DEPARTMENT','PROJECT','COMPANY')),
  constraint ck_workplace_document_status check(status in ('DRAFT','PUBLISHED','ARCHIVED')),
  constraint fk_workplace_document_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_document_folder foreign key(folder_id) references workplace_folders(id) on delete set null
);

create table workplace_document_versions (
  id char(36) primary key, company_id char(36) not null, document_id char(36) not null,
  version_number int not null, file_name varchar(255) not null, object_key varchar(500) not null,
  mime_type varchar(120) not null, size_bytes bigint not null, checksum_sha256 varchar(64) not null,
  change_note varchar(500) null, scan_status varchar(20) not null default 'PENDING', uploaded_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_document_version unique(document_id,version_number),
  constraint ck_workplace_document_size check(size_bytes>0 and size_bytes<=52428800),
  constraint ck_workplace_document_scan check(scan_status in ('PENDING','CLEAN','BLOCKED')),
  constraint fk_workplace_version_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_version_document foreign key(document_id) references workplace_documents(id) on delete cascade
);

create table workplace_document_access (
  document_id char(36) not null, company_id char(36) not null, subject varchar(100) not null,
  access_level varchar(20) not null default 'READ', granted_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6), primary key(document_id,subject),
  constraint ck_workplace_document_access check(access_level in ('READ','EDIT')),
  constraint fk_workplace_access_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_access_document foreign key(document_id) references workplace_documents(id) on delete cascade
);

create table workplace_kb_spaces (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(180) not null,
  description varchar(500) null, scope_type varchar(20) not null default 'COMPANY', scope_id char(36) null,
  owner_subject varchar(100) not null, archived boolean not null default false,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_kb_space unique(company_id,code),
  constraint ck_workplace_kb_space_scope check(scope_type in ('DEPARTMENT','PROJECT','COMPANY')),
  constraint fk_workplace_kb_space_company foreign key(company_id) references companies(id) on delete cascade
);

create table workplace_kb_articles (
  id char(36) primary key, company_id char(36) not null, space_id char(36) not null,
  slug varchar(180) not null, title varchar(255) not null, summary varchar(500) null, content text not null,
  status varchar(20) not null default 'DRAFT', version_number int not null default 1,
  author_subject varchar(100) not null, published_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_kb_slug unique(company_id,slug),
  constraint ck_workplace_kb_status check(status in ('DRAFT','PUBLISHED','ARCHIVED')),
  constraint fk_workplace_kb_article_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_kb_article_space foreign key(space_id) references workplace_kb_spaces(id) on delete cascade
);

create table workplace_approval_requests (
  id char(36) primary key, company_id char(36) not null, request_number varchar(80) not null,
  request_type varchar(60) not null, title varchar(255) not null, description varchar(1000) null,
  resource_type varchar(60) null, resource_id char(36) null, department_id char(36) null, project_id char(36) null,
  amount decimal(19,4) null, currency char(3) null, status varchar(30) not null default 'DRAFT',
  current_step int not null default 0, requester_subject varchar(100) not null,
  submitted_at timestamp(6) null, completed_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_approval unique(company_id,request_number),
  constraint ck_workplace_approval_amount check(amount is null or amount>=0),
  constraint ck_workplace_approval_status check(status in ('DRAFT','PENDING','APPROVED','REJECTED','CANCELLED')),
  constraint fk_workplace_approval_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_approval_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_workplace_approval_project foreign key(project_id) references pm_projects(id) on delete set null
);

create table workplace_approval_steps (
  id char(36) primary key, company_id char(36) not null, approval_request_id char(36) not null,
  step_order int not null, approver_subject varchar(100) not null, status varchar(20) not null default 'WAITING',
  decision_note varchar(500) null, decided_at timestamp(6) null,
  constraint uq_workplace_approval_step unique(approval_request_id,step_order),
  constraint ck_workplace_approval_step_order check(step_order>0),
  constraint ck_workplace_approval_step_status check(status in ('WAITING','PENDING','APPROVED','REJECTED','SKIPPED')),
  constraint fk_workplace_step_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_step_request foreign key(approval_request_id) references workplace_approval_requests(id) on delete cascade
);

create table workplace_approval_comments (
  id char(36) primary key, company_id char(36) not null, approval_request_id char(36) not null,
  author_subject varchar(100) not null, content varchar(2000) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_workplace_approval_comment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_approval_comment_request foreign key(approval_request_id) references workplace_approval_requests(id) on delete cascade
);

create table workplace_form_definitions (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(180) not null,
  description varchar(500) null, schema_json jsonb not null, status varchar(20) not null default 'DRAFT',
  requires_approval boolean not null default false, owner_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_form unique(company_id,code),
  constraint ck_workplace_form_status check(status in ('DRAFT','PUBLISHED','ARCHIVED')),
  constraint fk_workplace_form_company foreign key(company_id) references companies(id) on delete cascade
);

create table workplace_form_submissions (
  id char(36) primary key, company_id char(36) not null, form_definition_id char(36) not null,
  submission_number varchar(80) not null, payload_json jsonb not null, status varchar(30) not null default 'SUBMITTED',
  approval_request_id char(36) null, submitted_by_subject varchar(100) not null,
  submitted_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_submission unique(company_id,submission_number),
  constraint ck_workplace_submission_status check(status in ('SUBMITTED','PENDING_APPROVAL','APPROVED','REJECTED','ARCHIVED')),
  constraint fk_workplace_submission_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_submission_form foreign key(form_definition_id) references workplace_form_definitions(id),
  constraint fk_workplace_submission_approval foreign key(approval_request_id) references workplace_approval_requests(id) on delete set null
);

create table workplace_signature_requests (
  id char(36) primary key, company_id char(36) not null, request_number varchar(80) not null,
  document_id char(36) not null, title varchar(255) not null, message varchar(1000) null,
  status varchar(30) not null default 'DRAFT', expires_at timestamp(6) null, requested_by_subject varchar(100) not null,
  completed_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_signature unique(company_id,request_number),
  constraint ck_workplace_signature_status check(status in ('DRAFT','PENDING','COMPLETED','DECLINED','CANCELLED','EXPIRED')),
  constraint fk_workplace_signature_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_signature_document foreign key(document_id) references workplace_documents(id)
);

create table workplace_signature_recipients (
  id char(36) primary key, company_id char(36) not null, signature_request_id char(36) not null,
  signer_subject varchar(100) not null, sign_order int not null, status varchar(20) not null default 'WAITING',
  signed_at timestamp(6) null, evidence_hash varchar(64) null, decision_note varchar(500) null,
  constraint uq_workplace_signature_recipient unique(signature_request_id,signer_subject),
  constraint ck_workplace_signature_order check(sign_order>0),
  constraint ck_workplace_signature_recipient_status check(status in ('WAITING','PENDING','SIGNED','DECLINED','SKIPPED')),
  constraint fk_workplace_signature_recipient_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_signature_recipient_request foreign key(signature_request_id) references workplace_signature_requests(id) on delete cascade
);

create table workplace_helpdesk_tickets (
  id char(36) primary key, company_id char(36) not null, ticket_number varchar(80) not null,
  ticket_type varchar(40) not null, subject varchar(255) not null, description varchar(2000) not null,
  priority varchar(20) not null default 'NORMAL', status varchar(30) not null default 'OPEN',
  requester_subject varchar(100) not null, assigned_subject varchar(100) null, department_id char(36) null,
  due_at timestamp(6) null, resolved_at timestamp(6) null, satisfaction_score int null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_helpdesk unique(company_id,ticket_number),
  constraint ck_workplace_helpdesk_priority check(priority in ('LOW','NORMAL','HIGH','URGENT')),
  constraint ck_workplace_helpdesk_status check(status in ('OPEN','ASSIGNED','IN_PROGRESS','WAITING_USER','RESOLVED','CLOSED','CANCELLED')),
  constraint ck_workplace_helpdesk_score check(satisfaction_score is null or satisfaction_score between 1 and 5),
  constraint fk_workplace_helpdesk_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_helpdesk_department foreign key(department_id) references departments(id) on delete set null
);

create table workplace_helpdesk_comments (
  id char(36) primary key, company_id char(36) not null, ticket_id char(36) not null,
  author_subject varchar(100) not null, content varchar(2000) not null, internal_note boolean not null default false,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_workplace_helpdesk_comment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_helpdesk_comment_ticket foreign key(ticket_id) references workplace_helpdesk_tickets(id) on delete cascade
);

create table workplace_channels (
  id char(36) primary key, company_id char(36) not null, code varchar(80) not null, name varchar(180) not null,
  channel_type varchar(20) not null default 'COMPANY', department_id char(36) null, project_id char(36) null,
  description varchar(500) null, archived boolean not null default false, created_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_channel unique(company_id,code),
  constraint ck_workplace_channel_type check(channel_type in ('COMPANY','DEPARTMENT','PROJECT')),
  constraint fk_workplace_channel_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_channel_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_workplace_channel_project foreign key(project_id) references pm_projects(id) on delete set null
);

create table workplace_messages (
  id char(36) primary key, company_id char(36) not null, channel_id char(36) not null,
  parent_message_id char(36) null, author_subject varchar(100) not null, content varchar(4000) not null,
  edited_at timestamp(6) null, deleted_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_workplace_message_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_message_channel foreign key(channel_id) references workplace_channels(id) on delete cascade,
  constraint fk_workplace_message_parent foreign key(parent_message_id) references workplace_messages(id) on delete cascade
);

create table workplace_legal_records (
  id char(36) primary key, company_id char(36) not null, record_number varchar(80) not null,
  record_type varchar(50) not null, title varchar(255) not null, counterparty varchar(255) null,
  effective_on date null, expires_on date null, status varchar(30) not null default 'ACTIVE',
  owner_subject varchar(100) null, document_id char(36) null, notes varchar(2000) null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_workplace_legal_record unique(company_id,record_number),
  constraint ck_workplace_legal_dates check(expires_on is null or effective_on is null or expires_on>=effective_on),
  constraint ck_workplace_legal_status check(status in ('DRAFT','ACTIVE','EXPIRING','EXPIRED','TERMINATED','ARCHIVED')),
  constraint fk_workplace_legal_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_legal_document foreign key(document_id) references workplace_documents(id) on delete set null
);

create table workplace_legal_obligations (
  id char(36) primary key, company_id char(36) not null, legal_record_id char(36) not null,
  title varchar(255) not null, due_on date not null, recurrence varchar(30) null,
  owner_subject varchar(100) null, reminder_days int not null default 7, status varchar(20) not null default 'OPEN',
  completed_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint ck_workplace_obligation_reminder check(reminder_days>=0),
  constraint ck_workplace_obligation_status check(status in ('OPEN','COMPLETED','WAIVED')),
  constraint fk_workplace_obligation_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_workplace_obligation_record foreign key(legal_record_id) references workplace_legal_records(id) on delete cascade
);

create index ix_workplace_document_scope on workplace_documents(company_id,scope_type,scope_id,status);
create index ix_workplace_document_title on workplace_documents(company_id,title);
create index ix_workplace_kb_status on workplace_kb_articles(company_id,status,updated_at);
create index ix_workplace_approval_inbox on workplace_approval_steps(company_id,approver_subject,status);
create index ix_workplace_approval_requester on workplace_approval_requests(company_id,requester_subject,status);
create index ix_workplace_signature_inbox on workplace_signature_recipients(company_id,signer_subject,status);
create index ix_workplace_helpdesk_inbox on workplace_helpdesk_tickets(company_id,assigned_subject,status,priority);
create index ix_workplace_message_channel on workplace_messages(company_id,channel_id,created_at);
create index ix_workplace_legal_expiry on workplace_legal_records(company_id,status,expires_on);
create index ix_workplace_obligation_due on workplace_legal_obligations(company_id,status,due_on);

insert into workplace_kb_spaces(id,company_id,code,name,description,scope_type,owner_subject)
select substring(md5(c.id || ':KB:GENERAL'),1,8) || '-' || substring(md5(c.id || ':KB:GENERAL'),9,4) || '-' ||
       substring(md5(c.id || ':KB:GENERAL'),13,4) || '-' || substring(md5(c.id || ':KB:GENERAL'),17,4) || '-' ||
       substring(md5(c.id || ':KB:GENERAL'),21,12),c.id,'GENERAL','Tri thức chung','Quy định, hướng dẫn và SOP dùng chung','COMPANY','system'
from companies c on conflict(company_id,code) do nothing;

insert into workplace_channels(id,company_id,code,name,channel_type,description,created_by_subject)
select substring(md5(c.id || ':CHANNEL:GENERAL'),1,8) || '-' || substring(md5(c.id || ':CHANNEL:GENERAL'),9,4) || '-' ||
       substring(md5(c.id || ':CHANNEL:GENERAL'),13,4) || '-' || substring(md5(c.id || ':CHANNEL:GENERAL'),17,4) || '-' ||
       substring(md5(c.id || ':CHANNEL:GENERAL'),21,12),c.id,'GENERAL','Trao đổi chung','COMPANY','Kênh trao đổi toàn công ty','system'
from companies c on conflict(company_id,code) do nothing;
