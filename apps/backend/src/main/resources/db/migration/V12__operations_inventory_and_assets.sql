insert into permission_catalog(code,module,description,sensitive) values
('operations.access','OPERATIONS','Truy cập trung tâm vận hành',false),
('procurement.approve','OPERATIONS','Phê duyệt yêu cầu mua và đơn mua',true),
('inventory.count.approve','OPERATIONS','Phê duyệt và điều chỉnh kiểm kê',true),
('asset.read','OPERATIONS','Xem tài sản và lịch sử bàn giao',false),
('asset.write','OPERATIONS','Quản lý tài sản và bàn giao',true),
('maintenance.read','OPERATIONS','Xem bảo trì và sửa chữa',false),
('maintenance.write','OPERATIONS','Quản lý bảo trì và sửa chữa',true),
('license.read','OPERATIONS','Xem giấy phép phần mềm',false),
('license.write','OPERATIONS','Quản lý giấy phép phần mềm',true),
('manufacturing.read','OPERATIONS','Xem lệnh sản xuất',false),
('manufacturing.write','OPERATIONS','Quản lý lệnh sản xuất',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r cross join permission_catalog p
where r.code='COMPANY_ADMIN' and p.module='OPERATIONS'
on conflict(role_id,permission_code) do nothing;

insert into company_roles(id,company_id,code,name,description,system_role,active)
select substring(md5(c.id || ':ROLE:OPERATIONS_MANAGER'),1,8) || '-' || substring(md5(c.id || ':ROLE:OPERATIONS_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':ROLE:OPERATIONS_MANAGER'),13,4) || '-' || substring(md5(c.id || ':ROLE:OPERATIONS_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':ROLE:OPERATIONS_MANAGER'),21,12),c.id,'OPERATIONS_MANAGER','Quản lý vận hành',
       'Quản lý nhà cung cấp, mua sắm, kho, tài sản, bảo trì, giấy phép và giao nhận',true,true
from companies c on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','operations.access','supplier.read','supplier.write','procurement.read','procurement.write',
  'inventory.read','inventory.write','asset.read','asset.write','maintenance.read','maintenance.write',
  'license.read','license.write','manufacturing.read','manufacturing.write','fulfillment.read','fulfillment.write')
where r.code='OPERATIONS_MANAGER'
on conflict(role_id,permission_code) do nothing;

alter table suppliers add column if not exists tax_code varchar(80) null;
alter table suppliers add column if not exists email varchar(254) null;
alter table suppliers add column if not exists phone varchar(50) null;
alter table suppliers add column if not exists address text null;
alter table suppliers add column if not exists category varchar(80) null;
alter table suppliers add column if not exists bank_details text null;
alter table suppliers add column if not exists notes text null;

alter table supplier_performance_reviews add column if not exists service_score decimal(5,2) null;
alter table supplier_performance_reviews add column if not exists reviewed_by_subject varchar(100) null;

alter table purchase_requests add column if not exists department_id char(36) null references departments(id) on delete set null;
alter table purchase_requests add column if not exists project_id char(36) null references pm_projects(id) on delete set null;
alter table purchase_requests add column if not exists budget_id char(36) null references finance_budgets(id) on delete set null;
alter table purchase_requests add column if not exists approved_by_subject varchar(100) null;
alter table purchase_requests add column if not exists approved_at timestamp(6) null;
alter table purchase_requests add column if not exists decision_note varchar(500) null;
alter table purchase_requests add column if not exists budget_reserved_amount decimal(19,4) not null default 0;

alter table purchase_orders add column if not exists warehouse_id char(36) null references warehouses(id) on delete set null;
alter table purchase_orders add column if not exists ordered_on date null;
alter table purchase_orders add column if not exists approved_by_subject varchar(100) null;
alter table purchase_orders add column if not exists approved_at timestamp(6) null;
alter table purchase_orders add column if not exists created_by_subject varchar(100) null;

create table ops_inventory_items (
  id char(36) primary key, company_id char(36) not null, sku varchar(80) not null, name varchar(255) not null,
  item_type varchar(30) not null default 'MATERIAL', unit varchar(30) not null, min_stock decimal(19,4) not null default 0,
  standard_cost decimal(19,4) not null default 0, currency char(3) not null, active boolean not null default true,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_item unique(company_id,sku), constraint ck_ops_item_type check(item_type in ('MATERIAL','PRODUCT','CONSUMABLE','SPARE_PART','OTHER')),
  constraint ck_ops_item_values check(min_stock>=0 and standard_cost>=0),
  constraint fk_ops_item_company foreign key(company_id) references companies(id) on delete cascade
);

create table ops_purchase_request_items (
  id char(36) primary key, company_id char(36) not null, purchase_request_id char(36) not null,
  inventory_item_id char(36) null, description varchar(500) not null, quantity decimal(19,4) not null,
  unit varchar(30) not null, estimated_unit_price decimal(19,4) not null default 0,
  constraint ck_ops_pr_item_values check(quantity>0 and estimated_unit_price>=0),
  constraint fk_ops_pr_item_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_pr_item_request foreign key(purchase_request_id) references purchase_requests(id) on delete cascade,
  constraint fk_ops_pr_item_catalog foreign key(inventory_item_id) references ops_inventory_items(id) on delete set null
);

create table ops_purchase_order_items (
  id char(36) primary key, company_id char(36) not null, purchase_order_id char(36) not null,
  inventory_item_id char(36) null, description varchar(500) not null, quantity decimal(19,4) not null,
  received_quantity decimal(19,4) not null default 0, unit varchar(30) not null, unit_price decimal(19,4) not null default 0,
  constraint ck_ops_po_item_values check(quantity>0 and received_quantity>=0 and received_quantity<=quantity and unit_price>=0),
  constraint fk_ops_po_item_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_po_item_order foreign key(purchase_order_id) references purchase_orders(id) on delete cascade,
  constraint fk_ops_po_item_catalog foreign key(inventory_item_id) references ops_inventory_items(id) on delete set null
);

create table ops_goods_receipts (
  id char(36) primary key, company_id char(36) not null, receipt_number varchar(80) not null,
  purchase_order_id char(36) not null, warehouse_id char(36) not null, received_on date not null,
  status varchar(20) not null default 'POSTED', note varchar(500) null, received_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_receipt unique(company_id,receipt_number),
  constraint fk_ops_receipt_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_receipt_order foreign key(purchase_order_id) references purchase_orders(id),
  constraint fk_ops_receipt_warehouse foreign key(warehouse_id) references warehouses(id)
);

create table ops_goods_receipt_lines (
  id char(36) primary key, company_id char(36) not null, goods_receipt_id char(36) not null,
  purchase_order_item_id char(36) not null, inventory_item_id char(36) not null, quantity decimal(19,4) not null,
  constraint ck_ops_receipt_line_quantity check(quantity>0),
  constraint fk_ops_receipt_line_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_receipt_line_receipt foreign key(goods_receipt_id) references ops_goods_receipts(id) on delete cascade,
  constraint fk_ops_receipt_line_po_item foreign key(purchase_order_item_id) references ops_purchase_order_items(id),
  constraint fk_ops_receipt_line_item foreign key(inventory_item_id) references ops_inventory_items(id)
);

create table ops_stock_levels (
  id char(36) primary key, company_id char(36) not null, warehouse_id char(36) not null, inventory_item_id char(36) not null,
  on_hand decimal(19,4) not null default 0, committed decimal(19,4) not null default 0,
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_stock_level unique(warehouse_id,inventory_item_id),
  constraint ck_ops_stock_level check(on_hand>=0 and committed>=0 and committed<=on_hand),
  constraint fk_ops_stock_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_stock_warehouse foreign key(warehouse_id) references warehouses(id) on delete cascade,
  constraint fk_ops_stock_item foreign key(inventory_item_id) references ops_inventory_items(id) on delete cascade
);

create table ops_stock_movements (
  id char(36) primary key, company_id char(36) not null, movement_number varchar(80) not null,
  inventory_item_id char(36) not null, from_warehouse_id char(36) null, to_warehouse_id char(36) null,
  movement_type varchar(30) not null, quantity decimal(19,4) not null, occurred_at timestamp(6) not null default current_timestamp(6),
  reference_type varchar(40) null, reference_id char(36) null, note varchar(500) null, created_by_subject varchar(100) not null,
  constraint uq_ops_movement unique(company_id,movement_number), constraint ck_ops_movement_quantity check(quantity>0),
  constraint ck_ops_movement_type check(movement_type in ('RECEIPT','ISSUE','TRANSFER','ADJUSTMENT_IN','ADJUSTMENT_OUT','RETURN')),
  constraint ck_ops_movement_warehouses check(from_warehouse_id is not null or to_warehouse_id is not null),
  constraint fk_ops_movement_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_movement_item foreign key(inventory_item_id) references ops_inventory_items(id),
  constraint fk_ops_movement_from foreign key(from_warehouse_id) references warehouses(id) on delete set null,
  constraint fk_ops_movement_to foreign key(to_warehouse_id) references warehouses(id) on delete set null
);

create table ops_stock_counts (
  id char(36) primary key, company_id char(36) not null, count_number varchar(80) not null, warehouse_id char(36) not null,
  counted_on date not null, status varchar(20) not null default 'DRAFT', note varchar(500) null,
  created_by_subject varchar(100) not null, approved_by_subject varchar(100) null, approved_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_stock_count unique(company_id,count_number),
  constraint ck_ops_stock_count_status check(status in ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
  constraint fk_ops_count_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_count_warehouse foreign key(warehouse_id) references warehouses(id)
);

create table ops_stock_count_lines (
  id char(36) primary key, company_id char(36) not null, stock_count_id char(36) not null, inventory_item_id char(36) not null,
  system_quantity decimal(19,4) not null, counted_quantity decimal(19,4) not null,
  constraint uq_ops_count_line unique(stock_count_id,inventory_item_id), constraint ck_ops_count_line check(counted_quantity>=0),
  constraint fk_ops_count_line_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_count_line_count foreign key(stock_count_id) references ops_stock_counts(id) on delete cascade,
  constraint fk_ops_count_line_item foreign key(inventory_item_id) references ops_inventory_items(id)
);

create table ops_assets (
  id char(36) primary key, company_id char(36) not null, asset_code varchar(80) not null, name varchar(255) not null,
  asset_type varchar(60) not null, serial_number varchar(180) null, purchase_order_id char(36) null, warehouse_id char(36) null,
  custodian_employee_id char(36) null, department_id char(36) null, acquired_on date null, purchase_cost decimal(19,4) not null default 0,
  currency char(3) not null, warranty_until date null, condition_status varchar(30) not null default 'GOOD',
  status varchar(30) not null default 'AVAILABLE', note varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_asset unique(company_id,asset_code), constraint ck_ops_asset_cost check(purchase_cost>=0),
  constraint ck_ops_asset_status check(status in ('AVAILABLE','ASSIGNED','MAINTENANCE','RETIRED','LOST')),
  constraint fk_ops_asset_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_asset_po foreign key(purchase_order_id) references purchase_orders(id) on delete set null,
  constraint fk_ops_asset_warehouse foreign key(warehouse_id) references warehouses(id) on delete set null,
  constraint fk_ops_asset_employee foreign key(custodian_employee_id) references employees(id) on delete set null,
  constraint fk_ops_asset_department foreign key(department_id) references departments(id) on delete set null
);

create table ops_asset_assignments (
  id char(36) primary key, company_id char(36) not null, asset_id char(36) not null, employee_id char(36) not null,
  assigned_on date not null, returned_on date null, assigned_by_subject varchar(100) not null,
  return_condition varchar(60) null, note varchar(500) null,
  constraint fk_ops_assignment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_assignment_asset foreign key(asset_id) references ops_assets(id) on delete cascade,
  constraint fk_ops_assignment_employee foreign key(employee_id) references employees(id)
);

create table ops_maintenance_orders (
  id char(36) primary key, company_id char(36) not null, maintenance_number varchar(80) not null,
  asset_id char(36) not null, supplier_id char(36) null, maintenance_type varchar(30) not null,
  scheduled_on date null, completed_on date null, cost decimal(19,4) not null default 0, currency char(3) not null,
  status varchar(30) not null default 'OPEN', description varchar(500) not null, result varchar(500) null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_maintenance unique(company_id,maintenance_number), constraint ck_ops_maintenance_cost check(cost>=0),
  constraint ck_ops_maintenance_status check(status in ('OPEN','SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
  constraint fk_ops_maintenance_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_maintenance_asset foreign key(asset_id) references ops_assets(id),
  constraint fk_ops_maintenance_supplier foreign key(supplier_id) references suppliers(id) on delete set null
);

create table ops_software_licenses (
  id char(36) primary key, company_id char(36) not null, code varchar(80) not null, product_name varchar(255) not null,
  supplier_id char(36) null, owner_employee_id char(36) null, seat_count int not null default 1, assigned_seats int not null default 0,
  starts_on date null, renews_on date null, billing_cycle varchar(30) null, cost decimal(19,4) not null default 0,
  currency char(3) not null, status varchar(30) not null default 'ACTIVE', credential_reference varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_license unique(company_id,code), constraint ck_ops_license_seats check(seat_count>0 and assigned_seats>=0 and assigned_seats<=seat_count),
  constraint ck_ops_license_cost check(cost>=0),
  constraint fk_ops_license_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_license_supplier foreign key(supplier_id) references suppliers(id) on delete set null,
  constraint fk_ops_license_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table ops_manufacturing_orders (
  id char(36) primary key, company_id char(36) not null, order_number varchar(80) not null,
  inventory_item_id char(36) not null, warehouse_id char(36) not null, planned_quantity decimal(19,4) not null,
  completed_quantity decimal(19,4) not null default 0, planned_start date null, planned_end date null,
  status varchar(30) not null default 'DRAFT', note varchar(500) null, created_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_manufacturing unique(company_id,order_number),
  constraint ck_ops_manufacturing_quantity check(planned_quantity>0 and completed_quantity>=0 and completed_quantity<=planned_quantity),
  constraint ck_ops_manufacturing_status check(status in ('DRAFT','RELEASED','IN_PROGRESS','COMPLETED','CANCELLED')),
  constraint fk_ops_manufacturing_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_manufacturing_item foreign key(inventory_item_id) references ops_inventory_items(id),
  constraint fk_ops_manufacturing_warehouse foreign key(warehouse_id) references warehouses(id)
);

create table ops_shipments (
  id char(36) primary key, company_id char(36) not null, shipment_number varchar(80) not null,
  direction varchar(20) not null, purchase_order_id char(36) null, sales_order_id char(36) null,
  from_warehouse_id char(36) null, to_address text null, carrier varchar(120) null, tracking_number varchar(180) null,
  expected_on date null, shipped_at timestamp(6) null, delivered_at timestamp(6) null,
  status varchar(30) not null default 'PLANNED', note varchar(500) null, created_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6), updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_ops_shipment unique(company_id,shipment_number), constraint ck_ops_shipment_direction check(direction in ('INBOUND','OUTBOUND','TRANSFER')),
  constraint ck_ops_shipment_status check(status in ('PLANNED','READY','IN_TRANSIT','DELIVERED','EXCEPTION','CANCELLED')),
  constraint fk_ops_shipment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_ops_shipment_po foreign key(purchase_order_id) references purchase_orders(id) on delete set null,
  constraint fk_ops_shipment_order foreign key(sales_order_id) references crm_sales_orders(id) on delete set null,
  constraint fk_ops_shipment_warehouse foreign key(from_warehouse_id) references warehouses(id) on delete set null
);

create index ix_ops_pr_scope on purchase_requests(company_id,department_id,project_id,status);
create index ix_ops_po_status on purchase_orders(company_id,status,expected_on);
create index ix_ops_stock_item on ops_stock_levels(company_id,inventory_item_id,warehouse_id);
create index ix_ops_movement_date on ops_stock_movements(company_id,occurred_at);
create index ix_ops_asset_scope on ops_assets(company_id,department_id,custodian_employee_id,status);
create index ix_ops_maintenance_due on ops_maintenance_orders(company_id,status,scheduled_on);
create index ix_ops_license_renewal on ops_software_licenses(company_id,status,renews_on);
create index ix_ops_shipment_status on ops_shipments(company_id,status,expected_on);
