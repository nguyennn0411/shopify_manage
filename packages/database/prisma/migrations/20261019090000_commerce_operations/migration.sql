ALTER TABLE "orders"
  ADD COLUMN "customer_id" UUID,
  ADD COLUMN "tags" TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  ADD COLUMN "note" TEXT,
  ADD COLUMN "shipping_address" JSONB,
  ADD COLUMN "billing_address" JSONB;

CREATE TABLE "customers" (
  "id" UUID NOT NULL,
  "store_id" UUID NOT NULL,
  "shopify_id" TEXT NOT NULL,
  "display_name" TEXT NOT NULL,
  "email" TEXT,
  "phone" TEXT,
  "orders_count" INTEGER NOT NULL DEFAULT 0,
  "total_spent" DECIMAL(18,4) NOT NULL DEFAULT 0,
  "currency" TEXT NOT NULL,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "customers_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "refunds" (
  "id" UUID NOT NULL,
  "order_id" UUID NOT NULL,
  "shopify_id" TEXT NOT NULL,
  "amount" DECIMAL(18,4) NOT NULL,
  "currency" TEXT NOT NULL,
  "note" TEXT,
  "shopify_created_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "refunds_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "refund_line_items" (
  "id" UUID NOT NULL,
  "refund_id" UUID NOT NULL,
  "order_item_id" UUID,
  "quantity" INTEGER NOT NULL,
  "subtotal" DECIMAL(18,4) NOT NULL,
  CONSTRAINT "refund_line_items_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "fulfillments" (
  "id" UUID NOT NULL,
  "order_id" UUID NOT NULL,
  "shopify_id" TEXT NOT NULL,
  "status" TEXT NOT NULL,
  "tracking_company" TEXT,
  "tracking_number" TEXT,
  "tracking_url" TEXT,
  "shopify_created_at" TIMESTAMP(3) NOT NULL,
  "shopify_updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "fulfillments_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "fulfillment_line_items" (
  "id" UUID NOT NULL,
  "fulfillment_id" UUID NOT NULL,
  "order_item_id" UUID,
  "quantity" INTEGER NOT NULL,
  CONSTRAINT "fulfillment_line_items_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "order_events" (
  "id" UUID NOT NULL,
  "store_id" UUID NOT NULL,
  "order_id" UUID NOT NULL,
  "type" TEXT NOT NULL,
  "title" TEXT NOT NULL,
  "description" TEXT,
  "occurred_at" TIMESTAMP(3) NOT NULL,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "order_events_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "internal_skus" (
  "id" UUID NOT NULL,
  "company_id" UUID NOT NULL,
  "code" TEXT NOT NULL,
  "name" TEXT NOT NULL,
  "barcode" TEXT,
  "active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "internal_skus_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "variant_mappings" (
  "id" UUID NOT NULL,
  "internal_sku_id" UUID NOT NULL,
  "variant_id" UUID NOT NULL,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "variant_mappings_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "customers_store_id_shopify_id_key" ON "customers"("store_id", "shopify_id");
CREATE INDEX "customers_store_id_email_idx" ON "customers"("store_id", "email");
CREATE UNIQUE INDEX "refunds_order_id_shopify_id_key" ON "refunds"("order_id", "shopify_id");
CREATE UNIQUE INDEX "fulfillments_order_id_shopify_id_key" ON "fulfillments"("order_id", "shopify_id");
CREATE INDEX "order_events_order_id_occurred_at_idx" ON "order_events"("order_id", "occurred_at");
CREATE UNIQUE INDEX "order_events_order_id_type_title_occurred_at_key" ON "order_events"("order_id", "type", "title", "occurred_at");
CREATE UNIQUE INDEX "internal_skus_company_id_code_key" ON "internal_skus"("company_id", "code");
CREATE INDEX "internal_skus_company_id_name_idx" ON "internal_skus"("company_id", "name");
CREATE UNIQUE INDEX "variant_mappings_variant_id_key" ON "variant_mappings"("variant_id");
CREATE INDEX "variant_mappings_internal_sku_id_idx" ON "variant_mappings"("internal_sku_id");

ALTER TABLE "customers" ADD CONSTRAINT "customers_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "orders" ADD CONSTRAINT "orders_customer_id_fkey" FOREIGN KEY ("customer_id") REFERENCES "customers"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "refunds" ADD CONSTRAINT "refunds_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "refund_line_items" ADD CONSTRAINT "refund_line_items_refund_id_fkey" FOREIGN KEY ("refund_id") REFERENCES "refunds"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "refund_line_items" ADD CONSTRAINT "refund_line_items_order_item_id_fkey" FOREIGN KEY ("order_item_id") REFERENCES "order_items"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "fulfillments" ADD CONSTRAINT "fulfillments_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "fulfillment_line_items" ADD CONSTRAINT "fulfillment_line_items_fulfillment_id_fkey" FOREIGN KEY ("fulfillment_id") REFERENCES "fulfillments"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "fulfillment_line_items" ADD CONSTRAINT "fulfillment_line_items_order_item_id_fkey" FOREIGN KEY ("order_item_id") REFERENCES "order_items"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "order_events" ADD CONSTRAINT "order_events_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "order_events" ADD CONSTRAINT "order_events_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "internal_skus" ADD CONSTRAINT "internal_skus_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "variant_mappings" ADD CONSTRAINT "variant_mappings_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "variant_mappings" ADD CONSTRAINT "variant_mappings_variant_id_fkey" FOREIGN KEY ("variant_id") REFERENCES "variants"("id") ON DELETE CASCADE ON UPDATE CASCADE;
