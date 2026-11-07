create table companies (
  id char(36) primary key,
  name varchar(180) not null,
  code varchar(40) not null unique,
  base_currency char(3) not null default 'USD',
  timezone varchar(80) not null default 'UTC',
  status varchar(20) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6)
);

create table erp_users (
  id char(36) primary key,
  keycloak_subject varchar(100) not null unique,
  email varchar(254) not null,
  display_name varchar(180) not null,
  status varchar(20) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6)
);

create table company_memberships (
  company_id char(36) not null,
  keycloak_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  primary key(company_id, keycloak_subject),
  constraint fk_membership_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_membership_user foreign key(keycloak_subject) references erp_users(keycloak_subject) on delete cascade
);

create table stores (
  id char(36) primary key, company_id char(36) not null, name varchar(180) not null,
  shopify_domain varchar(255) not null, currency char(3) not null, status varchar(24) not null default 'DRAFT',
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_store_domain unique(company_id, shopify_domain), constraint fk_store_company foreign key(company_id) references companies(id) on delete cascade
);

create table shopify_connections (
  id char(36) primary key, company_id char(36) not null, store_id char(36) not null unique,
  encrypted_access_token text not null, encrypted_refresh_token text null, scopes text not null,
  token_expires_at timestamp(6) null, installed_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_shopify_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_shopify_store foreign key(store_id) references stores(id) on delete cascade
);

create table sync_jobs (
  id char(36) primary key, company_id char(36) not null, store_id char(36) not null,
  kind varchar(30) not null, status varchar(30) not null default 'QUEUED', processed bigint not null default 0,
  error_code varchar(80) null, safe_error_message varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6), started_at timestamp(6) null, completed_at timestamp(6) null,
  constraint fk_sync_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_sync_store foreign key(store_id) references stores(id) on delete cascade
);

create table products (
  id char(36) primary key, company_id char(36) not null, store_id char(36) null,
  shopify_id varchar(100) null, title varchar(255) not null, handle varchar(255) null,
  lifecycle_status varchar(30) not null default 'DRAFT', vendor varchar(180) null, product_type varchar(180) null,
  attributes text null, created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_product_shopify unique(store_id,shopify_id), constraint fk_product_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_product_store foreign key(store_id) references stores(id) on delete set null
);

create table variants (
  id char(36) primary key, company_id char(36) not null, product_id char(36) not null,
  shopify_id varchar(100) null, sku varchar(120) null, title varchar(255) not null,
  price decimal(19,4) not null default 0, cost decimal(19,4) null, barcode varchar(120) null, attributes text null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_variant_sku unique(company_id,sku), constraint fk_variant_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_variant_product foreign key(product_id) references products(id) on delete cascade
);

create table pim_profiles (
  id char(36) primary key, company_id char(36) not null, product_id char(36) not null unique,
  specifications text null, seo text null, shopify_sync_status varchar(30) not null default 'PENDING',
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_pim_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_pim_product foreign key(product_id) references products(id) on delete cascade
);

create table product_media (
  id char(36) primary key, company_id char(36) not null, product_id char(36) not null,
  object_key varchar(500) not null, media_type varchar(50) not null, alt_text varchar(500) null, position int not null default 0,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_media_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_media_product foreign key(product_id) references products(id) on delete cascade
);

create table warehouses (
  id char(36) primary key, company_id char(36) not null, code varchar(50) not null, name varchar(180) not null,
  address text null, status varchar(20) not null default 'ACTIVE', created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_warehouse_code unique(company_id,code), constraint fk_warehouse_company foreign key(company_id) references companies(id) on delete cascade
);

create table inventory_levels (
  id char(36) primary key, company_id char(36) not null, warehouse_id char(36) not null, variant_id char(36) not null,
  available decimal(19,4) not null default 0, committed decimal(19,4) not null default 0, incoming decimal(19,4) not null default 0,
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_inventory_level unique(warehouse_id,variant_id), constraint fk_inventory_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_inventory_warehouse foreign key(warehouse_id) references warehouses(id) on delete cascade,
  constraint fk_inventory_variant foreign key(variant_id) references variants(id) on delete cascade
);

create table customers (
  id char(36) primary key, company_id char(36) not null, store_id char(36) null, shopify_id varchar(100) null,
  email varchar(254) null, display_name varchar(180) null, attributes text null, created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_customer_shopify unique(store_id,shopify_id), constraint fk_customer_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_customer_store foreign key(store_id) references stores(id) on delete set null
);

create table orders (
  id char(36) primary key, company_id char(36) not null, store_id char(36) not null, customer_id char(36) null,
  shopify_id varchar(100) not null, order_number varchar(80) null, status varchar(40) not null,
  currency char(3) not null, subtotal decimal(19,4) not null default 0, total decimal(19,4) not null default 0,
  ordered_at timestamp(6) null, payload text null, created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_order_shopify unique(store_id,shopify_id), constraint fk_order_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_order_store foreign key(store_id) references stores(id) on delete cascade,
  constraint fk_order_customer foreign key(customer_id) references customers(id) on delete set null
);

create table suppliers (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(255) not null,
  contact text null, payment_terms varchar(255) null, lead_time_days int null, status varchar(20) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_supplier_code unique(company_id,code), constraint fk_supplier_company foreign key(company_id) references companies(id) on delete cascade
);

create table supplier_quotes (
  id char(36) primary key, company_id char(36) not null, supplier_id char(36) not null,
  quote_number varchar(80) not null, status varchar(30) not null default 'DRAFT', currency char(3) not null,
  valid_until date null, items text not null, total decimal(19,4) not null default 0,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_supplier_quote unique(company_id,quote_number), constraint fk_quote_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_quote_supplier foreign key(supplier_id) references suppliers(id) on delete cascade
);

create table supplier_contracts (
  id char(36) primary key, company_id char(36) not null, supplier_id char(36) not null,
  contract_number varchar(80) not null, status varchar(30) not null default 'DRAFT', starts_on date not null, ends_on date null,
  terms text null, private_object_key varchar(500) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_supplier_contract unique(company_id,contract_number), constraint fk_contract_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_contract_supplier foreign key(supplier_id) references suppliers(id) on delete cascade
);

create table supplier_performance_reviews (
  id char(36) primary key, company_id char(36) not null, supplier_id char(36) not null,
  period_start date not null, period_end date not null, quality_score decimal(5,2) null, delivery_score decimal(5,2) null, cost_score decimal(5,2) null,
  notes text null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_review_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_review_supplier foreign key(supplier_id) references suppliers(id) on delete cascade
);

create table purchase_requests (
  id char(36) primary key, company_id char(36) not null, request_number varchar(80) not null,
  requester_subject varchar(100) not null, status varchar(30) not null default 'DRAFT', required_on date null,
  purpose varchar(500) null, items text not null, estimated_total decimal(19,4) not null default 0, currency char(3) not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_purchase_request unique(company_id,request_number), constraint fk_request_company foreign key(company_id) references companies(id) on delete cascade
);

create table purchase_orders (
  id char(36) primary key, company_id char(36) not null, supplier_id char(36) not null, purchase_request_id char(36) null,
  po_number varchar(80) not null, status varchar(30) not null default 'DRAFT', currency char(3) not null, total decimal(19,4) not null default 0,
  expected_on date null, received_at timestamp(6) null, items text not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_purchase_order unique(company_id,po_number), constraint fk_po_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_po_supplier foreign key(supplier_id) references suppliers(id), constraint fk_po_request foreign key(purchase_request_id) references purchase_requests(id) on delete set null
);

create table procurement_payments (
  id char(36) primary key, company_id char(36) not null, purchase_order_id char(36) not null,
  status varchar(30) not null default 'PENDING', amount decimal(19,4) not null, currency char(3) not null,
  due_on date null, paid_at timestamp(6) null, reference varchar(120) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_payment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_payment_po foreign key(purchase_order_id) references purchase_orders(id) on delete cascade
);

create table fulfillment_jobs (
  id char(36) primary key, company_id char(36) not null, order_id char(36) not null, warehouse_id char(36) not null,
  status varchar(30) not null default 'PENDING', assigned_subject varchar(100) null, packed_at timestamp(6) null, completed_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_fulfillment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_fulfillment_order foreign key(order_id) references orders(id), constraint fk_fulfillment_warehouse foreign key(warehouse_id) references warehouses(id)
);

create table shipments (
  id char(36) primary key, company_id char(36) not null, fulfillment_job_id char(36) not null,
  carrier varchar(100) null, service varchar(100) null, tracking_number varchar(180) null, tracking_url varchar(500) null,
  status varchar(40) not null default 'PENDING', events text null, shipped_at timestamp(6) null, delivered_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_shipment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_shipment_job foreign key(fulfillment_job_id) references fulfillment_jobs(id) on delete cascade
);

create table return_requests (
  id char(36) primary key, company_id char(36) not null, order_id char(36) not null,
  status varchar(30) not null default 'REQUESTED', reason varchar(500) null, items text not null,
  requested_at timestamp(6) not null default current_timestamp(6), received_at timestamp(6) null, refunded_at timestamp(6) null,
  constraint fk_return_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_return_order foreign key(order_id) references orders(id)
);

create table departments (
  id char(36) primary key, company_id char(36) not null, code varchar(50) not null, name varchar(180) not null,
  function_type varchar(40) not null, constraint uq_department unique(company_id,code),
  constraint fk_department_company foreign key(company_id) references companies(id) on delete cascade
);

create table product_department_links (
  company_id char(36) not null, product_id char(36) not null, department_id char(36) not null, owner_subject varchar(100) null,
  primary key(product_id,department_id), constraint fk_pdl_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_pdl_product foreign key(product_id) references products(id) on delete cascade,
  constraint fk_pdl_department foreign key(department_id) references departments(id) on delete cascade
);

create table marketing_connections (
  id char(36) primary key, company_id char(36) not null, platform varchar(40) not null,
  encrypted_credentials text not null, status varchar(30) not null default 'CONNECTED', created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_marketing_company foreign key(company_id) references companies(id) on delete cascade
);

create table expenses (
  id char(36) primary key, company_id char(36) not null, category varchar(100) not null, description varchar(500) not null,
  amount decimal(19,4) not null, currency char(3) not null, status varchar(30) not null default 'DRAFT', incurred_on date not null,
  created_at timestamp(6) not null default current_timestamp(6), constraint fk_expense_company foreign key(company_id) references companies(id) on delete cascade
);

create table audit_logs (
  id char(36) primary key, company_id char(36) null, actor_subject varchar(100) null,
  action varchar(100) not null, resource_type varchar(80) not null, resource_id varchar(180) not null,
  metadata text null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_audit_company foreign key(company_id) references companies(id) on delete cascade
);
