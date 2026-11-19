insert into permission_catalog(code,module,description,sensitive) values
('crm.access','CRM','Truy cập không gian CRM',false),
('crm.customer.read','CRM','Xem khách hàng và lịch sử tương tác',true),
('crm.customer.write','CRM','Tạo và cập nhật khách hàng',true),
('crm.pipeline.read','CRM','Xem lead và cơ hội kinh doanh',true),
('crm.pipeline.write','CRM','Quản lý lead và pipeline bán hàng',true),
('crm.quote.read','CRM','Xem báo giá',true),
('crm.quote.write','CRM','Tạo và sửa báo giá',true),
('crm.quote.approve','CRM','Phê duyệt hoặc từ chối báo giá',true),
('crm.contract.read','CRM','Xem hợp đồng khách hàng',true),
('crm.contract.write','CRM','Quản lý hợp đồng và lịch thanh toán',true),
('crm.order.read','CRM','Xem đơn bán nội bộ',true),
('crm.order.write','CRM','Quản lý đơn bán và trả hàng',true),
('crm.support.read','CRM','Xem ticket chăm sóc khách hàng',true),
('crm.support.write','CRM','Xử lý ticket và SLA',true),
('crm.marketing.read','CRM','Xem chiến dịch và hiệu quả marketing',true),
('crm.marketing.write','CRM','Quản lý chiến dịch marketing',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.module='CRM'
where r.code='COMPANY_ADMIN'
on conflict do nothing;

insert into company_roles(id,company_id,code,name,description,system_role)
select substring(md5(c.id || ':SALES_CRM'),1,8) || '-' || substring(md5(c.id || ':SALES_CRM'),9,4) || '-' ||
       substring(md5(c.id || ':SALES_CRM'),13,4) || '-' || substring(md5(c.id || ':SALES_CRM'),17,4) || '-' ||
       substring(md5(c.id || ':SALES_CRM'),21,12), c.id,'SALES_CRM','Kinh doanh & CRM',
       'Quản lý khách hàng, pipeline, báo giá, hợp đồng, đơn bán và chăm sóc khách hàng trong phạm vi được giao',true
from companies c
on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','crm.access','crm.customer.read','crm.customer.write','crm.pipeline.read','crm.pipeline.write',
  'crm.quote.read','crm.quote.write','crm.contract.read','crm.contract.write','crm.order.read','crm.order.write',
  'crm.support.read','crm.support.write','crm.marketing.read','crm.marketing.write'
)
where r.code='SALES_CRM'
on conflict do nothing;

alter table customers add column customer_code varchar(60) null;
alter table customers add column customer_type varchar(20) not null default 'ORGANIZATION';
alter table customers add column phone varchar(40) null;
alter table customers add column tax_code varchar(60) null;
alter table customers add column website varchar(500) null;
alter table customers add column address text null;
alter table customers add column segment varchar(80) null;
alter table customers add column source varchar(80) null;
alter table customers add column status varchar(20) not null default 'ACTIVE';
alter table customers add column owner_employee_id char(36) null;
alter table customers add column notes text null;
alter table customers add column created_by_subject varchar(100) null;
alter table customers add constraint fk_customer_owner foreign key(owner_employee_id) references employees(id) on delete set null;
create unique index uq_customer_code on customers(company_id,customer_code) where customer_code is not null;
create index ix_customer_company_owner on customers(company_id,owner_employee_id,status);

create table crm_contacts (
  id char(36) primary key, company_id char(36) not null, customer_id char(36) not null,
  full_name varchar(180) not null, job_title varchar(120) null, email varchar(254) null, phone varchar(40) null,
  is_primary boolean not null default false, notes text null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_crm_contact_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_contact_customer foreign key(customer_id) references customers(id) on delete cascade
);

create table crm_campaigns (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(255) not null,
  campaign_type varchar(40) not null, channel varchar(60) null, status varchar(20) not null default 'PLANNED',
  starts_on date null, ends_on date null, budget decimal(19,4) not null default 0, actual_cost decimal(19,4) not null default 0,
  currency char(3) not null, leads_generated int not null default 0, conversions int not null default 0,
  owner_employee_id char(36) null, objective text null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_campaign unique(company_id,code),
  constraint fk_crm_campaign_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_campaign_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table crm_leads (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, display_name varchar(180) not null,
  organization_name varchar(255) null, email varchar(254) null, phone varchar(40) null, source varchar(80) null,
  status varchar(30) not null default 'NEW', score int not null default 0, estimated_value decimal(19,4) null,
  currency char(3) null, owner_employee_id char(36) null, campaign_id char(36) null, customer_id char(36) null,
  notes text null, created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_lead unique(company_id,code),
  constraint fk_crm_lead_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_lead_owner foreign key(owner_employee_id) references employees(id) on delete set null,
  constraint fk_crm_lead_campaign foreign key(campaign_id) references crm_campaigns(id) on delete set null,
  constraint fk_crm_lead_customer foreign key(customer_id) references customers(id) on delete set null
);

create table crm_pipeline_stages (
  id char(36) primary key, company_id char(36) not null, code varchar(40) not null, name varchar(120) not null,
  probability int not null default 0, sort_order int not null default 0, is_won boolean not null default false,
  is_lost boolean not null default false, active boolean not null default true,
  constraint uq_crm_stage unique(company_id,code),
  constraint fk_crm_stage_company foreign key(company_id) references companies(id) on delete cascade
);

insert into crm_pipeline_stages(id,company_id,code,name,probability,sort_order,is_won,is_lost)
select substring(md5(c.id || ':' || s.code),1,8) || '-' || substring(md5(c.id || ':' || s.code),9,4) || '-' ||
       substring(md5(c.id || ':' || s.code),13,4) || '-' || substring(md5(c.id || ':' || s.code),17,4) || '-' ||
       substring(md5(c.id || ':' || s.code),21,12), c.id,s.code,s.name,s.probability,s.sort_order,s.is_won,s.is_lost
from companies c cross join (values
  ('QUALIFICATION','Tiếp nhận & đánh giá',10,0,false,false),
  ('DISCOVERY','Khảo sát nhu cầu',25,1,false,false),
  ('PROPOSAL','Đề xuất giải pháp',50,2,false,false),
  ('NEGOTIATION','Đàm phán',75,3,false,false),
  ('WON','Thành công',100,4,true,false),
  ('LOST','Thất bại',0,5,false,true)
) as s(code,name,probability,sort_order,is_won,is_lost)
on conflict(company_id,code) do nothing;

create table crm_opportunities (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, title varchar(255) not null,
  customer_id char(36) not null, lead_id char(36) null, stage_id char(36) not null, value decimal(19,4) not null default 0,
  currency char(3) not null, probability int not null default 0, expected_close_on date null, owner_employee_id char(36) null,
  next_step varchar(500) null, lost_reason varchar(500) null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_opportunity unique(company_id,code),
  constraint fk_crm_opportunity_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_opportunity_customer foreign key(customer_id) references customers(id),
  constraint fk_crm_opportunity_lead foreign key(lead_id) references crm_leads(id) on delete set null,
  constraint fk_crm_opportunity_stage foreign key(stage_id) references crm_pipeline_stages(id),
  constraint fk_crm_opportunity_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table crm_interactions (
  id char(36) primary key, company_id char(36) not null, customer_id char(36) not null, opportunity_id char(36) null,
  interaction_type varchar(40) not null, subject varchar(255) not null, details text null,
  occurred_at timestamp(6) not null, created_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_crm_interaction_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_interaction_customer foreign key(customer_id) references customers(id) on delete cascade,
  constraint fk_crm_interaction_opportunity foreign key(opportunity_id) references crm_opportunities(id) on delete set null
);

create table crm_quotes (
  id char(36) primary key, company_id char(36) not null, quote_number varchar(80) not null, version int not null default 1,
  customer_id char(36) not null, opportunity_id char(36) null, status varchar(30) not null default 'DRAFT',
  currency char(3) not null, subtotal decimal(19,4) not null default 0, discount_amount decimal(19,4) not null default 0,
  tax_amount decimal(19,4) not null default 0, total decimal(19,4) not null default 0, valid_until date null,
  terms text null, created_by_subject varchar(100) not null, approved_by_subject varchar(100) null,
  approved_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_quote_version unique(company_id,quote_number,version),
  constraint fk_crm_quote_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_quote_customer foreign key(customer_id) references customers(id),
  constraint fk_crm_quote_opportunity foreign key(opportunity_id) references crm_opportunities(id) on delete set null
);

create table crm_quote_items (
  id char(36) primary key, company_id char(36) not null, quote_id char(36) not null,
  item_code varchar(80) null, description varchar(500) not null, quantity decimal(19,4) not null,
  unit_price decimal(19,4) not null, discount_percent decimal(5,2) not null default 0, line_total decimal(19,4) not null,
  constraint fk_crm_quote_item_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_quote_item_quote foreign key(quote_id) references crm_quotes(id) on delete cascade
);

create table crm_contracts (
  id char(36) primary key, company_id char(36) not null, contract_number varchar(80) not null, title varchar(255) not null,
  customer_id char(36) not null, quote_id char(36) null, status varchar(30) not null default 'DRAFT',
  starts_on date not null, ends_on date null, value decimal(19,4) not null default 0, currency char(3) not null,
  terms text null, private_object_key varchar(500) null, renewal_notice_days int not null default 30,
  owner_employee_id char(36) null, signed_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_contract unique(company_id,contract_number),
  constraint fk_crm_contract_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_contract_customer foreign key(customer_id) references customers(id),
  constraint fk_crm_contract_quote foreign key(quote_id) references crm_quotes(id) on delete set null,
  constraint fk_crm_contract_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table crm_contract_payments (
  id char(36) primary key, company_id char(36) not null, contract_id char(36) not null,
  installment_name varchar(180) not null, amount decimal(19,4) not null, due_on date not null,
  status varchar(20) not null default 'PENDING', paid_at timestamp(6) null, reference varchar(120) null,
  constraint fk_crm_contract_payment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_contract_payment_contract foreign key(contract_id) references crm_contracts(id) on delete cascade
);

create table crm_sales_orders (
  id char(36) primary key, company_id char(36) not null, order_number varchar(80) not null,
  customer_id char(36) not null, quote_id char(36) null, contract_id char(36) null,
  status varchar(30) not null default 'DRAFT', currency char(3) not null, subtotal decimal(19,4) not null default 0,
  discount_amount decimal(19,4) not null default 0, total decimal(19,4) not null default 0,
  ordered_on date null, expected_delivery_on date null, owner_employee_id char(36) null, notes text null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_sales_order unique(company_id,order_number),
  constraint fk_crm_order_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_order_customer foreign key(customer_id) references customers(id),
  constraint fk_crm_order_quote foreign key(quote_id) references crm_quotes(id) on delete set null,
  constraint fk_crm_order_contract foreign key(contract_id) references crm_contracts(id) on delete set null,
  constraint fk_crm_order_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table crm_sales_order_items (
  id char(36) primary key, company_id char(36) not null, sales_order_id char(36) not null,
  item_code varchar(80) null, description varchar(500) not null, quantity decimal(19,4) not null,
  unit_price decimal(19,4) not null, line_total decimal(19,4) not null,
  constraint fk_crm_order_item_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_order_item_order foreign key(sales_order_id) references crm_sales_orders(id) on delete cascade
);

create table crm_sales_returns (
  id char(36) primary key, company_id char(36) not null, sales_order_id char(36) not null,
  status varchar(30) not null default 'REQUESTED', reason varchar(500) not null, amount decimal(19,4) not null default 0,
  requested_at timestamp(6) not null default current_timestamp(6), completed_at timestamp(6) null,
  constraint fk_crm_return_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_return_order foreign key(sales_order_id) references crm_sales_orders(id) on delete cascade
);

create table crm_support_tickets (
  id char(36) primary key, company_id char(36) not null, ticket_number varchar(80) not null,
  customer_id char(36) not null, contact_id char(36) null, sales_order_id char(36) null,
  subject varchar(255) not null, description text null, channel varchar(30) not null default 'WEB',
  priority varchar(20) not null default 'NORMAL', status varchar(30) not null default 'OPEN',
  assignee_employee_id char(36) null, sla_due_at timestamp(6) null, first_response_at timestamp(6) null,
  resolved_at timestamp(6) null, resolution text null, satisfaction_score int null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_ticket unique(company_id,ticket_number),
  constraint fk_crm_ticket_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_ticket_customer foreign key(customer_id) references customers(id),
  constraint fk_crm_ticket_contact foreign key(contact_id) references crm_contacts(id) on delete set null,
  constraint fk_crm_ticket_order foreign key(sales_order_id) references crm_sales_orders(id) on delete set null,
  constraint fk_crm_ticket_assignee foreign key(assignee_employee_id) references employees(id) on delete set null
);

create table crm_ticket_comments (
  id char(36) primary key, company_id char(36) not null, ticket_id char(36) not null,
  body text not null, internal_note boolean not null default false, author_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_crm_ticket_comment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_crm_ticket_comment_ticket foreign key(ticket_id) references crm_support_tickets(id) on delete cascade
);

create index ix_crm_contact_customer on crm_contacts(company_id,customer_id);
create index ix_crm_lead_owner_status on crm_leads(company_id,owner_employee_id,status);
create index ix_crm_opportunity_owner_stage on crm_opportunities(company_id,owner_employee_id,stage_id);
create index ix_crm_quote_customer_status on crm_quotes(company_id,customer_id,status);
create index ix_crm_contract_customer_status on crm_contracts(company_id,customer_id,status,ends_on);
create index ix_crm_order_customer_status on crm_sales_orders(company_id,customer_id,status);
create index ix_crm_ticket_customer_status on crm_support_tickets(company_id,customer_id,status,sla_due_at);
create index ix_crm_campaign_status_dates on crm_campaigns(company_id,status,starts_on,ends_on);
