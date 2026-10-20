CREATE TYPE "PurchaseOrderStatus" AS ENUM ('DRAFT','SUBMITTED','APPROVED','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED');
CREATE TYPE "StockMovementType" AS ENUM ('RECEIPT','ADJUSTMENT','TRANSFER','RETURN_IN','RETURN_OUT');
CREATE TYPE "StockMovementStatus" AS ENUM ('DRAFT','POSTED','REVERSED');
CREATE TYPE "StockCountStatus" AS ENUM ('DRAFT','SUBMITTED','APPROVED','REJECTED');
CREATE TYPE "ReturnStatus" AS ENUM ('REQUESTED','APPROVED','RECEIVED','REFUNDED','REJECTED');
CREATE TYPE "ApprovalStatus" AS ENUM ('PENDING','APPROVED','REJECTED','CANCELLED');

CREATE TABLE "warehouses" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "code" TEXT NOT NULL, "name" TEXT NOT NULL, "address" JSONB,
  "active" BOOLEAN NOT NULL DEFAULT true, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "warehouses_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "suppliers" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "code" TEXT NOT NULL, "name" TEXT NOT NULL, "contact_name" TEXT, "email" TEXT,
  "phone" TEXT, "address" JSONB, "payment_terms" TEXT, "active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "suppliers_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "purchase_orders" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "supplier_id" UUID NOT NULL, "warehouse_id" UUID NOT NULL, "number" TEXT NOT NULL,
  "status" "PurchaseOrderStatus" NOT NULL DEFAULT 'DRAFT', "currency" TEXT NOT NULL, "expected_at" TIMESTAMP(3), "note" TEXT,
  "submitted_by_id" UUID, "approved_by_id" UUID, "approved_at" TIMESTAMP(3), "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL, CONSTRAINT "purchase_orders_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "purchase_order_items" (
  "id" UUID NOT NULL, "purchase_order_id" UUID NOT NULL, "internal_sku_id" UUID NOT NULL, "quantity" INTEGER NOT NULL,
  "received_quantity" INTEGER NOT NULL DEFAULT 0, "unit_cost" DECIMAL(18,4) NOT NULL, CONSTRAINT "purchase_order_items_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "stock_movements" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "type" "StockMovementType" NOT NULL, "status" "StockMovementStatus" NOT NULL DEFAULT 'DRAFT',
  "from_warehouse_id" UUID, "to_warehouse_id" UUID, "purchase_order_id" UUID, "reference" TEXT, "note" TEXT, "created_by_id" UUID NOT NULL,
  "posted_by_id" UUID, "posted_at" TIMESTAMP(3), "reversed_at" TIMESTAMP(3), "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "stock_movements_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "stock_movement_lines" (
  "id" UUID NOT NULL, "movement_id" UUID NOT NULL, "internal_sku_id" UUID NOT NULL, "quantity" INTEGER NOT NULL,
  "unit_cost" DECIMAL(18,4), CONSTRAINT "stock_movement_lines_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "warehouse_balances" (
  "id" UUID NOT NULL, "warehouse_id" UUID NOT NULL, "internal_sku_id" UUID NOT NULL, "quantity" INTEGER NOT NULL DEFAULT 0,
  "updated_at" TIMESTAMP(3) NOT NULL, CONSTRAINT "warehouse_balances_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "stock_counts" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "warehouse_id" UUID NOT NULL, "status" "StockCountStatus" NOT NULL DEFAULT 'DRAFT',
  "counted_at" TIMESTAMP(3) NOT NULL, "note" TEXT, "created_by_id" UUID NOT NULL, "approved_by_id" UUID, "approved_at" TIMESTAMP(3),
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT "stock_counts_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "stock_count_items" (
  "id" UUID NOT NULL, "stock_count_id" UUID NOT NULL, "internal_sku_id" UUID NOT NULL, "expected" INTEGER NOT NULL,
  "counted" INTEGER NOT NULL, "variance" INTEGER NOT NULL, CONSTRAINT "stock_count_items_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "return_requests" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "store_id" UUID NOT NULL, "order_id" UUID NOT NULL, "number" TEXT NOT NULL,
  "status" "ReturnStatus" NOT NULL DEFAULT 'REQUESTED', "reason" TEXT NOT NULL, "requested_by_id" UUID NOT NULL, "approved_by_id" UUID,
  "approved_at" TIMESTAMP(3), "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "return_requests_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "return_items" (
  "id" UUID NOT NULL, "return_request_id" UUID NOT NULL, "order_item_id" UUID NOT NULL, "internal_sku_id" UUID,
  "quantity" INTEGER NOT NULL, "condition" TEXT, CONSTRAINT "return_items_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "approval_requests" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "entity_type" TEXT NOT NULL, "entity_id" UUID NOT NULL,
  "status" "ApprovalStatus" NOT NULL DEFAULT 'PENDING', "requested_by_id" UUID NOT NULL, "decided_by_id" UUID, "decision_note" TEXT,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "decided_at" TIMESTAMP(3), CONSTRAINT "approval_requests_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "warehouses_company_id_code_key" ON "warehouses"("company_id","code");
CREATE UNIQUE INDEX "suppliers_company_id_code_key" ON "suppliers"("company_id","code");
CREATE INDEX "suppliers_company_id_name_idx" ON "suppliers"("company_id","name");
CREATE UNIQUE INDEX "purchase_orders_company_id_number_key" ON "purchase_orders"("company_id","number");
CREATE INDEX "purchase_orders_company_id_status_idx" ON "purchase_orders"("company_id","status");
CREATE UNIQUE INDEX "purchase_order_items_purchase_order_id_internal_sku_id_key" ON "purchase_order_items"("purchase_order_id","internal_sku_id");
CREATE INDEX "stock_movements_company_id_created_at_idx" ON "stock_movements"("company_id","created_at");
CREATE UNIQUE INDEX "stock_movement_lines_movement_id_internal_sku_id_key" ON "stock_movement_lines"("movement_id","internal_sku_id");
CREATE UNIQUE INDEX "warehouse_balances_warehouse_id_internal_sku_id_key" ON "warehouse_balances"("warehouse_id","internal_sku_id");
CREATE INDEX "stock_counts_company_id_counted_at_idx" ON "stock_counts"("company_id","counted_at");
CREATE UNIQUE INDEX "stock_count_items_stock_count_id_internal_sku_id_key" ON "stock_count_items"("stock_count_id","internal_sku_id");
CREATE UNIQUE INDEX "return_requests_company_id_number_key" ON "return_requests"("company_id","number");
CREATE INDEX "return_requests_store_id_status_idx" ON "return_requests"("store_id","status");
CREATE UNIQUE INDEX "return_items_return_request_id_order_item_id_key" ON "return_items"("return_request_id","order_item_id");
CREATE INDEX "approval_requests_company_id_status_created_at_idx" ON "approval_requests"("company_id","status","created_at");
CREATE UNIQUE INDEX "approval_requests_entity_type_entity_id_status_key" ON "approval_requests"("entity_type","entity_id","status");

ALTER TABLE "warehouses" ADD CONSTRAINT "warehouses_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "user_role_assignments" ADD CONSTRAINT "user_role_assignments_warehouse_id_fkey" FOREIGN KEY ("warehouse_id") REFERENCES "warehouses"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "suppliers" ADD CONSTRAINT "suppliers_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "purchase_orders" ADD CONSTRAINT "purchase_orders_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "purchase_orders" ADD CONSTRAINT "purchase_orders_supplier_id_fkey" FOREIGN KEY ("supplier_id") REFERENCES "suppliers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "purchase_orders" ADD CONSTRAINT "purchase_orders_warehouse_id_fkey" FOREIGN KEY ("warehouse_id") REFERENCES "warehouses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "purchase_order_items" ADD CONSTRAINT "purchase_order_items_purchase_order_id_fkey" FOREIGN KEY ("purchase_order_id") REFERENCES "purchase_orders"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "purchase_order_items" ADD CONSTRAINT "purchase_order_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "stock_movements" ADD CONSTRAINT "stock_movements_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "stock_movements" ADD CONSTRAINT "stock_movements_from_warehouse_id_fkey" FOREIGN KEY ("from_warehouse_id") REFERENCES "warehouses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "stock_movements" ADD CONSTRAINT "stock_movements_to_warehouse_id_fkey" FOREIGN KEY ("to_warehouse_id") REFERENCES "warehouses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "stock_movements" ADD CONSTRAINT "stock_movements_purchase_order_id_fkey" FOREIGN KEY ("purchase_order_id") REFERENCES "purchase_orders"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "stock_movement_lines" ADD CONSTRAINT "stock_movement_lines_movement_id_fkey" FOREIGN KEY ("movement_id") REFERENCES "stock_movements"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "stock_movement_lines" ADD CONSTRAINT "stock_movement_lines_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "warehouse_balances" ADD CONSTRAINT "warehouse_balances_warehouse_id_fkey" FOREIGN KEY ("warehouse_id") REFERENCES "warehouses"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "warehouse_balances" ADD CONSTRAINT "warehouse_balances_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "stock_counts" ADD CONSTRAINT "stock_counts_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "stock_counts" ADD CONSTRAINT "stock_counts_warehouse_id_fkey" FOREIGN KEY ("warehouse_id") REFERENCES "warehouses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "stock_count_items" ADD CONSTRAINT "stock_count_items_stock_count_id_fkey" FOREIGN KEY ("stock_count_id") REFERENCES "stock_counts"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "stock_count_items" ADD CONSTRAINT "stock_count_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "return_requests" ADD CONSTRAINT "return_requests_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "return_requests" ADD CONSTRAINT "return_requests_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "return_requests" ADD CONSTRAINT "return_requests_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "return_items" ADD CONSTRAINT "return_items_return_request_id_fkey" FOREIGN KEY ("return_request_id") REFERENCES "return_requests"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "return_items" ADD CONSTRAINT "return_items_order_item_id_fkey" FOREIGN KEY ("order_item_id") REFERENCES "order_items"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "return_items" ADD CONSTRAINT "return_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "approval_requests" ADD CONSTRAINT "approval_requests_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
