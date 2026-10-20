-- CreateEnum
CREATE TYPE "SupplierQuoteStatus" AS ENUM ('DRAFT', 'RECEIVED', 'ACCEPTED', 'REJECTED', 'EXPIRED');

-- CreateEnum
CREATE TYPE "SupplierContractStatus" AS ENUM ('DRAFT', 'ACTIVE', 'EXPIRED', 'TERMINATED');

-- CreateEnum
CREATE TYPE "PurchaseRequestStatus" AS ENUM ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CONVERTED', 'CANCELLED');

-- CreateEnum
CREATE TYPE "ProcurementPaymentStatus" AS ENUM ('PLANNED', 'PENDING_APPROVAL', 'APPROVED', 'PAID', 'VOID');

-- CreateEnum
CREATE TYPE "ProductLifecycleStatus" AS ENUM ('IDEA', 'DEVELOPMENT', 'READY', 'ACTIVE', 'DISCONTINUED');

-- CreateEnum
CREATE TYPE "BusinessFunction" AS ENUM ('RND', 'SALES', 'MARKETING', 'FINANCE');

-- CreateEnum
CREATE TYPE "FulfillmentJobStatus" AS ENUM ('OPEN', 'PICKING', 'PACKED', 'SHIPPED', 'DELIVERED', 'EXCEPTION', 'CANCELLED', 'RETURNED');

-- CreateEnum
CREATE TYPE "ShipmentEventStatus" AS ENUM ('LABEL_CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'EXCEPTION', 'RETURNED');

-- AlterTable
ALTER TABLE "purchase_orders" ADD COLUMN     "purchase_request_id" UUID,
ADD COLUMN     "supplier_quote_id" UUID;

-- CreateTable
CREATE TABLE "supplier_quotes" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "supplier_id" UUID NOT NULL,
    "number" TEXT NOT NULL,
    "status" "SupplierQuoteStatus" NOT NULL DEFAULT 'DRAFT',
    "currency" TEXT NOT NULL,
    "valid_until" TIMESTAMP(3),
    "lead_time_days" INTEGER,
    "payment_terms" TEXT,
    "notes" TEXT,
    "created_by_id" UUID NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "supplier_quotes_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "supplier_quote_items" (
    "id" UUID NOT NULL,
    "quote_id" UUID NOT NULL,
    "internal_sku_id" UUID NOT NULL,
    "quantity" INTEGER NOT NULL,
    "min_order_qty" INTEGER NOT NULL DEFAULT 1,
    "unit_price" DECIMAL(18,4) NOT NULL,

    CONSTRAINT "supplier_quote_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "supplier_contracts" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "supplier_id" UUID NOT NULL,
    "number" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "status" "SupplierContractStatus" NOT NULL DEFAULT 'DRAFT',
    "start_date" TIMESTAMP(3) NOT NULL,
    "end_date" TIMESTAMP(3),
    "currency" TEXT NOT NULL,
    "value" DECIMAL(18,4),
    "terms" TEXT,
    "document_url" TEXT,
    "created_by_id" UUID NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "supplier_contracts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "supplier_performance_reviews" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "supplier_id" UUID NOT NULL,
    "period_start" TIMESTAMP(3) NOT NULL,
    "period_end" TIMESTAMP(3) NOT NULL,
    "quality_score" INTEGER NOT NULL,
    "delivery_score" INTEGER NOT NULL,
    "price_score" INTEGER NOT NULL,
    "service_score" INTEGER NOT NULL,
    "overall_score" DECIMAL(5,2) NOT NULL,
    "notes" TEXT,
    "reviewed_by_id" UUID NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "supplier_performance_reviews_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "purchase_requests" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "department_id" UUID,
    "number" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "status" "PurchaseRequestStatus" NOT NULL DEFAULT 'DRAFT',
    "priority" "WorkTaskPriority" NOT NULL DEFAULT 'MEDIUM',
    "needed_by" TIMESTAMP(3),
    "justification" TEXT,
    "requested_by_id" UUID NOT NULL,
    "approved_by_id" UUID,
    "approved_at" TIMESTAMP(3),
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "purchase_requests_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "purchase_request_items" (
    "id" UUID NOT NULL,
    "purchase_request_id" UUID NOT NULL,
    "internal_sku_id" UUID NOT NULL,
    "quantity" INTEGER NOT NULL,
    "target_unit_cost" DECIMAL(18,4),
    "notes" TEXT,

    CONSTRAINT "purchase_request_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "procurement_payments" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "purchase_order_id" UUID NOT NULL,
    "reference" TEXT NOT NULL,
    "status" "ProcurementPaymentStatus" NOT NULL DEFAULT 'PLANNED',
    "amount" DECIMAL(18,4) NOT NULL,
    "currency" TEXT NOT NULL,
    "due_date" TIMESTAMP(3),
    "paid_at" TIMESTAMP(3),
    "payment_method" TEXT,
    "external_reference" TEXT,
    "created_by_id" UUID NOT NULL,
    "approved_by_id" UUID,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "procurement_payments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "pim_profiles" (
    "internal_sku_id" UUID NOT NULL,
    "lifecycle_status" "ProductLifecycleStatus" NOT NULL DEFAULT 'IDEA',
    "brand" TEXT,
    "category" TEXT,
    "description" TEXT,
    "specifications" JSONB,
    "hs_code" TEXT,
    "country_of_origin" TEXT,
    "weight_grams" INTEGER,
    "dimensions" JSONB,
    "seo" JSONB,
    "published" BOOLEAN NOT NULL DEFAULT false,
    "updated_by_id" UUID NOT NULL,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "pim_profiles_pkey" PRIMARY KEY ("internal_sku_id")
);

-- CreateTable
CREATE TABLE "product_media" (
    "id" UUID NOT NULL,
    "pim_profile_id" UUID NOT NULL,
    "type" TEXT NOT NULL,
    "url" TEXT NOT NULL,
    "alt_text" TEXT,
    "position" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "product_media_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "product_department_links" (
    "id" UUID NOT NULL,
    "internal_sku_id" UUID NOT NULL,
    "department_id" UUID NOT NULL,
    "function" "BusinessFunction" NOT NULL,
    "owner_note" TEXT,

    CONSTRAINT "product_department_links_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "fulfillment_jobs" (
    "id" UUID NOT NULL,
    "company_id" UUID NOT NULL,
    "order_id" UUID NOT NULL,
    "warehouse_id" UUID NOT NULL,
    "number" TEXT NOT NULL,
    "status" "FulfillmentJobStatus" NOT NULL DEFAULT 'OPEN',
    "priority" "WorkTaskPriority" NOT NULL DEFAULT 'MEDIUM',
    "assigned_to_id" UUID,
    "promised_at" TIMESTAMP(3),
    "packed_at" TIMESTAMP(3),
    "shipped_at" TIMESTAMP(3),
    "delivered_at" TIMESTAMP(3),
    "note" TEXT,
    "created_by_id" UUID NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "fulfillment_jobs_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "fulfillment_job_items" (
    "id" UUID NOT NULL,
    "fulfillment_job_id" UUID NOT NULL,
    "order_item_id" UUID NOT NULL,
    "internal_sku_id" UUID,
    "quantity" INTEGER NOT NULL,
    "picked_quantity" INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT "fulfillment_job_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "fulfillment_shipments" (
    "id" UUID NOT NULL,
    "fulfillment_job_id" UUID NOT NULL,
    "carrier" TEXT NOT NULL,
    "service" TEXT,
    "tracking_number" TEXT NOT NULL,
    "tracking_url" TEXT,
    "label_url" TEXT,
    "shipping_cost" DECIMAL(18,4),
    "currency" TEXT,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "fulfillment_shipments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "shipment_events" (
    "id" UUID NOT NULL,
    "shipment_id" UUID NOT NULL,
    "status" "ShipmentEventStatus" NOT NULL,
    "location" TEXT,
    "description" TEXT,
    "occurred_at" TIMESTAMP(3) NOT NULL,
    "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "shipment_events_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "supplier_quotes_supplier_id_status_idx" ON "supplier_quotes"("supplier_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "supplier_quotes_company_id_number_key" ON "supplier_quotes"("company_id", "number");

-- CreateIndex
CREATE UNIQUE INDEX "supplier_quote_items_quote_id_internal_sku_id_key" ON "supplier_quote_items"("quote_id", "internal_sku_id");

-- CreateIndex
CREATE INDEX "supplier_contracts_supplier_id_status_idx" ON "supplier_contracts"("supplier_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "supplier_contracts_company_id_number_key" ON "supplier_contracts"("company_id", "number");

-- CreateIndex
CREATE INDEX "supplier_performance_reviews_supplier_id_period_end_idx" ON "supplier_performance_reviews"("supplier_id", "period_end");

-- CreateIndex
CREATE INDEX "purchase_requests_company_id_status_created_at_idx" ON "purchase_requests"("company_id", "status", "created_at");

-- CreateIndex
CREATE UNIQUE INDEX "purchase_requests_company_id_number_key" ON "purchase_requests"("company_id", "number");

-- CreateIndex
CREATE UNIQUE INDEX "purchase_request_items_purchase_request_id_internal_sku_id_key" ON "purchase_request_items"("purchase_request_id", "internal_sku_id");

-- CreateIndex
CREATE INDEX "procurement_payments_purchase_order_id_status_idx" ON "procurement_payments"("purchase_order_id", "status");

-- CreateIndex
CREATE UNIQUE INDEX "procurement_payments_company_id_reference_key" ON "procurement_payments"("company_id", "reference");

-- CreateIndex
CREATE INDEX "product_media_pim_profile_id_position_idx" ON "product_media"("pim_profile_id", "position");

-- CreateIndex
CREATE INDEX "product_department_links_department_id_idx" ON "product_department_links"("department_id");

-- CreateIndex
CREATE UNIQUE INDEX "product_department_links_internal_sku_id_function_key" ON "product_department_links"("internal_sku_id", "function");

-- CreateIndex
CREATE INDEX "fulfillment_jobs_warehouse_id_status_created_at_idx" ON "fulfillment_jobs"("warehouse_id", "status", "created_at");

-- CreateIndex
CREATE INDEX "fulfillment_jobs_order_id_idx" ON "fulfillment_jobs"("order_id");

-- CreateIndex
CREATE UNIQUE INDEX "fulfillment_jobs_company_id_number_key" ON "fulfillment_jobs"("company_id", "number");

-- CreateIndex
CREATE UNIQUE INDEX "fulfillment_job_items_fulfillment_job_id_order_item_id_key" ON "fulfillment_job_items"("fulfillment_job_id", "order_item_id");

-- CreateIndex
CREATE UNIQUE INDEX "fulfillment_shipments_fulfillment_job_id_key" ON "fulfillment_shipments"("fulfillment_job_id");

-- CreateIndex
CREATE INDEX "fulfillment_shipments_tracking_number_idx" ON "fulfillment_shipments"("tracking_number");

-- CreateIndex
CREATE INDEX "shipment_events_shipment_id_occurred_at_idx" ON "shipment_events"("shipment_id", "occurred_at");

-- AddForeignKey
ALTER TABLE "purchase_orders" ADD CONSTRAINT "purchase_orders_purchase_request_id_fkey" FOREIGN KEY ("purchase_request_id") REFERENCES "purchase_requests"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "purchase_orders" ADD CONSTRAINT "purchase_orders_supplier_quote_id_fkey" FOREIGN KEY ("supplier_quote_id") REFERENCES "supplier_quotes"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_quotes" ADD CONSTRAINT "supplier_quotes_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_quotes" ADD CONSTRAINT "supplier_quotes_supplier_id_fkey" FOREIGN KEY ("supplier_id") REFERENCES "suppliers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_quote_items" ADD CONSTRAINT "supplier_quote_items_quote_id_fkey" FOREIGN KEY ("quote_id") REFERENCES "supplier_quotes"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_quote_items" ADD CONSTRAINT "supplier_quote_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_contracts" ADD CONSTRAINT "supplier_contracts_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_contracts" ADD CONSTRAINT "supplier_contracts_supplier_id_fkey" FOREIGN KEY ("supplier_id") REFERENCES "suppliers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_performance_reviews" ADD CONSTRAINT "supplier_performance_reviews_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "supplier_performance_reviews" ADD CONSTRAINT "supplier_performance_reviews_supplier_id_fkey" FOREIGN KEY ("supplier_id") REFERENCES "suppliers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "purchase_requests" ADD CONSTRAINT "purchase_requests_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "purchase_requests" ADD CONSTRAINT "purchase_requests_department_id_fkey" FOREIGN KEY ("department_id") REFERENCES "departments"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "purchase_request_items" ADD CONSTRAINT "purchase_request_items_purchase_request_id_fkey" FOREIGN KEY ("purchase_request_id") REFERENCES "purchase_requests"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "purchase_request_items" ADD CONSTRAINT "purchase_request_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "procurement_payments" ADD CONSTRAINT "procurement_payments_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "procurement_payments" ADD CONSTRAINT "procurement_payments_purchase_order_id_fkey" FOREIGN KEY ("purchase_order_id") REFERENCES "purchase_orders"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "pim_profiles" ADD CONSTRAINT "pim_profiles_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "product_media" ADD CONSTRAINT "product_media_pim_profile_id_fkey" FOREIGN KEY ("pim_profile_id") REFERENCES "pim_profiles"("internal_sku_id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "product_department_links" ADD CONSTRAINT "product_department_links_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "product_department_links" ADD CONSTRAINT "product_department_links_department_id_fkey" FOREIGN KEY ("department_id") REFERENCES "departments"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_jobs" ADD CONSTRAINT "fulfillment_jobs_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_jobs" ADD CONSTRAINT "fulfillment_jobs_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_jobs" ADD CONSTRAINT "fulfillment_jobs_warehouse_id_fkey" FOREIGN KEY ("warehouse_id") REFERENCES "warehouses"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_job_items" ADD CONSTRAINT "fulfillment_job_items_fulfillment_job_id_fkey" FOREIGN KEY ("fulfillment_job_id") REFERENCES "fulfillment_jobs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_job_items" ADD CONSTRAINT "fulfillment_job_items_order_item_id_fkey" FOREIGN KEY ("order_item_id") REFERENCES "order_items"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_job_items" ADD CONSTRAINT "fulfillment_job_items_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "fulfillment_shipments" ADD CONSTRAINT "fulfillment_shipments_fulfillment_job_id_fkey" FOREIGN KEY ("fulfillment_job_id") REFERENCES "fulfillment_jobs"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "shipment_events" ADD CONSTRAINT "shipment_events_shipment_id_fkey" FOREIGN KEY ("shipment_id") REFERENCES "fulfillment_shipments"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- RenameIndex
ALTER INDEX "marketing_daily_metrics_connection_id_metric_date_campaign_id_k" RENAME TO "marketing_daily_metrics_connection_id_metric_date_campaign__key";
