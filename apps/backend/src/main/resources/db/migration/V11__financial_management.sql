insert into permission_catalog(code,module,description,sensitive) values
('finance.access','FINANCE','Truy cập không gian tài chính quản trị',true),
('finance.transaction.read','FINANCE','Xem phiếu thu chi',true),
('finance.transaction.write','FINANCE','Tạo và cập nhật phiếu thu chi',true),
('finance.transaction.approve','FINANCE','Phê duyệt và ghi sổ phiếu thu chi',true),
('finance.budget.read','FINANCE','Xem ngân sách',true),
('finance.budget.write','FINANCE','Lập và điều chỉnh ngân sách',true),
('finance.invoice.read','FINANCE','Xem hóa đơn và công nợ',true),
('finance.invoice.write','FINANCE','Quản lý hóa đơn và thanh toán',true),
('finance.advance.read','FINANCE','Xem tạm ứng và hoàn ứng',true),
('finance.advance.write','FINANCE','Tạo tạm ứng và nộp quyết toán',true),
('finance.advance.approve','FINANCE','Phê duyệt và giải ngân tạm ứng',true),
('finance.project-cost.read','FINANCE','Xem chi phí dự án',true),
('finance.project-cost.write','FINANCE','Ghi nhận chi phí dự án',true),
('finance.accounting.read','FINANCE','Xem hệ thống tài khoản và sổ cái',true),
('finance.accounting.write','FINANCE','Tạo bút toán kế toán quản trị',true),
('finance.period.close','FINANCE','Khóa hoặc mở kỳ kế toán',true),
('finance.report.read','FINANCE','Xem báo cáo tài chính quản trị',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.module='FINANCE'
where r.code='COMPANY_ADMIN' on conflict do nothing;

insert into company_roles(id,company_id,code,name,description,system_role)
select substring(md5(c.id || ':FINANCE_MANAGER'),1,8) || '-' || substring(md5(c.id || ':FINANCE_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':FINANCE_MANAGER'),13,4) || '-' || substring(md5(c.id || ':FINANCE_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':FINANCE_MANAGER'),21,12),c.id,'FINANCE_MANAGER','Tài chính & Kế toán quản trị',
       'Quản lý thu chi, ngân sách, công nợ, hóa đơn, tạm ứng, chi phí dự án và sổ kế toán trong phạm vi được giao',true
from companies c on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','finance.access','finance.transaction.read','finance.transaction.write','finance.transaction.approve',
  'finance.budget.read','finance.budget.write','finance.invoice.read','finance.invoice.write',
  'finance.advance.read','finance.advance.write','finance.advance.approve','finance.project-cost.read',
  'finance.project-cost.write','finance.accounting.read','finance.accounting.write','finance.report.read'
)
where r.code='FINANCE_MANAGER' on conflict do nothing;

create table finance_categories (
  id char(36) primary key, company_id char(36) not null, code varchar(50) not null, name varchar(180) not null,
  category_type varchar(20) not null, active boolean not null default true,
  constraint uq_finance_category unique(company_id,code),
  constraint ck_finance_category_type check(category_type in ('INCOME','EXPENSE')),
  constraint fk_finance_category_company foreign key(company_id) references companies(id) on delete cascade
);

insert into finance_categories(id,company_id,code,name,category_type)
select substring(md5(c.id || ':FINCAT:' || x.code),1,8) || '-' || substring(md5(c.id || ':FINCAT:' || x.code),9,4) || '-' ||
       substring(md5(c.id || ':FINCAT:' || x.code),13,4) || '-' || substring(md5(c.id || ':FINCAT:' || x.code),17,4) || '-' ||
       substring(md5(c.id || ':FINCAT:' || x.code),21,12),c.id,x.code,x.name,x.category_type
from companies c cross join (values
 ('SALES_REVENUE','Doanh thu bán hàng','INCOME'),('SERVICE_REVENUE','Doanh thu dịch vụ','INCOME'),
 ('OTHER_INCOME','Thu nhập khác','INCOME'),('PAYROLL','Chi phí nhân sự','EXPENSE'),
 ('SOFTWARE','Phần mềm & dịch vụ','EXPENSE'),('OFFICE','Văn phòng','EXPENSE'),
 ('MARKETING','Marketing','EXPENSE'),('TRAVEL','Công tác','EXPENSE'),('OTHER_EXPENSE','Chi phí khác','EXPENSE')
) as x(code,name,category_type) on conflict(company_id,code) do nothing;

create table finance_budgets (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(255) not null,
  department_id char(36) null, project_id char(36) null, category_id char(36) null,
  period_start date not null, period_end date not null, currency char(3) not null,
  planned_amount decimal(19,4) not null, committed_amount decimal(19,4) not null default 0,
  actual_amount decimal(19,4) not null default 0, status varchar(20) not null default 'DRAFT', notes text null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_finance_budget unique(company_id,code),
  constraint ck_finance_budget_dates check(period_end>=period_start),
  constraint ck_finance_budget_amounts check(planned_amount>=0 and committed_amount>=0 and actual_amount>=0),
  constraint fk_finance_budget_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_budget_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_finance_budget_project foreign key(project_id) references pm_projects(id) on delete set null,
  constraint fk_finance_budget_category foreign key(category_id) references finance_categories(id) on delete set null
);

create table finance_cash_transactions (
  id char(36) primary key, company_id char(36) not null, transaction_number varchar(80) not null,
  transaction_type varchar(20) not null, category_id char(36) not null, department_id char(36) null,
  project_id char(36) null, customer_id char(36) null, supplier_id char(36) null,
  amount decimal(19,4) not null, currency char(3) not null, occurred_on date not null,
  payment_method varchar(30) null, reference varchar(120) null, description varchar(500) not null,
  document_object_key varchar(500) null, status varchar(20) not null default 'DRAFT',
  requested_by_subject varchar(100) not null, approved_by_subject varchar(100) null, approved_at timestamp(6) null,
  posted_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_finance_cash_tx unique(company_id,transaction_number),
  constraint ck_finance_cash_type check(transaction_type in ('RECEIPT','PAYMENT')),
  constraint ck_finance_cash_amount check(amount>0),
  constraint fk_finance_cash_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_cash_category foreign key(category_id) references finance_categories(id),
  constraint fk_finance_cash_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_finance_cash_project foreign key(project_id) references pm_projects(id) on delete set null,
  constraint fk_finance_cash_customer foreign key(customer_id) references customers(id) on delete set null,
  constraint fk_finance_cash_supplier foreign key(supplier_id) references suppliers(id) on delete set null
);

create table finance_invoices (
  id char(36) primary key, company_id char(36) not null, invoice_number varchar(80) not null,
  direction varchar(20) not null, customer_id char(36) null, supplier_id char(36) null,
  sales_order_id char(36) null, contract_id char(36) null, project_id char(36) null, department_id char(36) null,
  issue_date date not null, due_date date not null, subtotal decimal(19,4) not null,
  tax_amount decimal(19,4) not null default 0, total_amount decimal(19,4) not null,
  paid_amount decimal(19,4) not null default 0, currency char(3) not null,
  status varchar(30) not null default 'OPEN', description varchar(500) null, document_object_key varchar(500) null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_finance_invoice unique(company_id,invoice_number),
  constraint ck_finance_invoice_direction check(direction in ('RECEIVABLE','PAYABLE')),
  constraint ck_finance_invoice_dates check(due_date>=issue_date),
  constraint ck_finance_invoice_amounts check(subtotal>=0 and tax_amount>=0 and total_amount>=0 and paid_amount>=0 and paid_amount<=total_amount),
  constraint fk_finance_invoice_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_invoice_customer foreign key(customer_id) references customers(id) on delete set null,
  constraint fk_finance_invoice_supplier foreign key(supplier_id) references suppliers(id) on delete set null,
  constraint fk_finance_invoice_order foreign key(sales_order_id) references crm_sales_orders(id) on delete set null,
  constraint fk_finance_invoice_contract foreign key(contract_id) references crm_contracts(id) on delete set null,
  constraint fk_finance_invoice_project foreign key(project_id) references pm_projects(id) on delete set null,
  constraint fk_finance_invoice_department foreign key(department_id) references departments(id) on delete set null
);

create table finance_invoice_payments (
  id char(36) primary key, company_id char(36) not null, invoice_id char(36) not null,
  amount decimal(19,4) not null, paid_on date not null, payment_method varchar(30) null,
  reference varchar(120) null, notes varchar(500) null, recorded_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint ck_finance_invoice_payment_amount check(amount>0),
  constraint fk_finance_invoice_payment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_invoice_payment_invoice foreign key(invoice_id) references finance_invoices(id) on delete cascade
);

create table finance_advances (
  id char(36) primary key, company_id char(36) not null, advance_number varchar(80) not null,
  employee_id char(36) not null, department_id char(36) null, project_id char(36) null,
  purpose varchar(500) not null, requested_amount decimal(19,4) not null, disbursed_amount decimal(19,4) not null default 0,
  expense_amount decimal(19,4) not null default 0, returned_amount decimal(19,4) not null default 0,
  currency char(3) not null, needed_on date not null, status varchar(30) not null default 'DRAFT',
  requested_by_subject varchar(100) not null, approved_by_subject varchar(100) null, approved_at timestamp(6) null,
  settled_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_finance_advance unique(company_id,advance_number),
  constraint ck_finance_advance_amounts check(requested_amount>0 and disbursed_amount>=0 and expense_amount>=0 and returned_amount>=0),
  constraint fk_finance_advance_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_advance_employee foreign key(employee_id) references employees(id),
  constraint fk_finance_advance_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_finance_advance_project foreign key(project_id) references pm_projects(id) on delete set null
);

create table finance_advance_expenses (
  id char(36) primary key, company_id char(36) not null, advance_id char(36) not null,
  category_id char(36) not null, amount decimal(19,4) not null, incurred_on date not null,
  description varchar(500) not null, document_object_key varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint ck_finance_advance_expense_amount check(amount>0),
  constraint fk_finance_advance_expense_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_advance_expense_parent foreign key(advance_id) references finance_advances(id) on delete cascade,
  constraint fk_finance_advance_expense_category foreign key(category_id) references finance_categories(id)
);

create table finance_project_costs (
  id char(36) primary key, company_id char(36) not null, project_id char(36) not null,
  category_id char(36) not null, cost_type varchar(30) not null, amount decimal(19,4) not null,
  currency char(3) not null, incurred_on date not null, source_type varchar(40) null, source_id char(36) null,
  description varchar(500) not null, status varchar(20) not null default 'POSTED', created_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint ck_finance_project_cost_amount check(amount>0),
  constraint fk_finance_project_cost_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_project_cost_project foreign key(project_id) references pm_projects(id),
  constraint fk_finance_project_cost_category foreign key(category_id) references finance_categories(id)
);

create table finance_accounts (
  id char(36) primary key, company_id char(36) not null, code varchar(30) not null, name varchar(180) not null,
  account_type varchar(20) not null, parent_account_id char(36) null, active boolean not null default true,
  constraint uq_finance_account unique(company_id,code),
  constraint ck_finance_account_type check(account_type in ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
  constraint fk_finance_account_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_account_parent foreign key(parent_account_id) references finance_accounts(id) on delete set null
);

insert into finance_accounts(id,company_id,code,name,account_type)
select substring(md5(c.id || ':FINACC:' || x.code),1,8) || '-' || substring(md5(c.id || ':FINACC:' || x.code),9,4) || '-' ||
       substring(md5(c.id || ':FINACC:' || x.code),13,4) || '-' || substring(md5(c.id || ':FINACC:' || x.code),17,4) || '-' ||
       substring(md5(c.id || ':FINACC:' || x.code),21,12),c.id,x.code,x.name,x.account_type
from companies c cross join (values
 ('111','Tiền mặt','ASSET'),('112','Tiền gửi ngân hàng','ASSET'),('131','Phải thu khách hàng','ASSET'),
 ('141','Tạm ứng','ASSET'),('331','Phải trả nhà cung cấp','LIABILITY'),('411','Vốn chủ sở hữu','EQUITY'),
 ('511','Doanh thu','REVENUE'),('642','Chi phí quản lý','EXPENSE')
) as x(code,name,account_type) on conflict(company_id,code) do nothing;

create table finance_periods (
  id char(36) primary key, company_id char(36) not null, period_year int not null, period_month int not null,
  status varchar(20) not null default 'OPEN', closed_by_subject varchar(100) null, closed_at timestamp(6) null,
  constraint uq_finance_period unique(company_id,period_year,period_month),
  constraint ck_finance_period_month check(period_month between 1 and 12),
  constraint fk_finance_period_company foreign key(company_id) references companies(id) on delete cascade
);

create table finance_journals (
  id char(36) primary key, company_id char(36) not null, journal_number varchar(80) not null,
  journal_date date not null, description varchar(500) not null, reference varchar(120) null,
  status varchar(20) not null default 'DRAFT', created_by_subject varchar(100) not null,
  posted_by_subject varchar(100) null, posted_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_finance_journal unique(company_id,journal_number),
  constraint fk_finance_journal_company foreign key(company_id) references companies(id) on delete cascade
);

create table finance_journal_lines (
  id char(36) primary key, company_id char(36) not null, journal_id char(36) not null,
  account_id char(36) not null, department_id char(36) null, project_id char(36) null,
  description varchar(500) null, debit decimal(19,4) not null default 0, credit decimal(19,4) not null default 0,
  constraint ck_finance_journal_line check(debit>=0 and credit>=0 and ((debit>0 and credit=0) or (credit>0 and debit=0))),
  constraint fk_finance_journal_line_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_finance_journal_line_journal foreign key(journal_id) references finance_journals(id) on delete cascade,
  constraint fk_finance_journal_line_account foreign key(account_id) references finance_accounts(id),
  constraint fk_finance_journal_line_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_finance_journal_line_project foreign key(project_id) references pm_projects(id) on delete set null
);

create index ix_finance_budget_scope on finance_budgets(company_id,department_id,project_id,period_start,period_end,status);
create index ix_finance_cash_status on finance_cash_transactions(company_id,status,occurred_on);
create index ix_finance_invoice_due on finance_invoices(company_id,direction,status,due_date);
create index ix_finance_advance_status on finance_advances(company_id,status,needed_on);
create index ix_finance_project_cost on finance_project_costs(company_id,project_id,incurred_on);
create index ix_finance_journal_date on finance_journals(company_id,status,journal_date);
