CREATE TYPE "ExpenseStatus" AS ENUM ('DRAFT', 'APPROVED', 'VOID');

ALTER TABLE "order_items"
  ADD COLUMN "cost_unit_snapshot" DECIMAL(18,4),
  ADD COLUMN "cost_total_snapshot" DECIMAL(18,4);

ALTER TABLE "refund_line_items"
  ADD COLUMN "cost_reversal" DECIMAL(18,4) NOT NULL DEFAULT 0;

CREATE TABLE "cost_price_history" (
  "id" UUID NOT NULL,
  "internal_sku_id" UUID NOT NULL,
  "amount" DECIMAL(18,4) NOT NULL,
  "currency" TEXT NOT NULL,
  "effective_from" TIMESTAMP(3) NOT NULL,
  "note" TEXT,
  "created_by_id" UUID,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "cost_price_history_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "expense_categories" (
  "id" UUID NOT NULL,
  "company_id" UUID NOT NULL,
  "name" TEXT NOT NULL,
  "code" TEXT NOT NULL,
  "active" BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT "expense_categories_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "expenses" (
  "id" UUID NOT NULL,
  "company_id" UUID NOT NULL,
  "store_id" UUID,
  "order_id" UUID,
  "category_id" UUID NOT NULL,
  "amount" DECIMAL(18,4) NOT NULL,
  "currency" TEXT NOT NULL,
  "expense_date" TIMESTAMP(3) NOT NULL,
  "description" TEXT NOT NULL,
  "reference" TEXT,
  "status" "ExpenseStatus" NOT NULL DEFAULT 'DRAFT',
  "created_by_id" UUID NOT NULL,
  "approved_by_id" UUID,
  "approved_at" TIMESTAMP(3),
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "expenses_pkey" PRIMARY KEY ("id")
);

CREATE TABLE "profit_snapshots" (
  "id" UUID NOT NULL,
  "order_id" UUID NOT NULL,
  "currency" TEXT NOT NULL,
  "gross_revenue" DECIMAL(18,4) NOT NULL,
  "discounts" DECIMAL(18,4) NOT NULL,
  "refunds" DECIMAL(18,4) NOT NULL,
  "shipping_income" DECIMAL(18,4) NOT NULL,
  "taxes" DECIMAL(18,4) NOT NULL,
  "net_revenue" DECIMAL(18,4) NOT NULL,
  "cogs" DECIMAL(18,4) NOT NULL,
  "transaction_fees" DECIMAL(18,4) NOT NULL DEFAULT 0,
  "expenses" DECIMAL(18,4) NOT NULL DEFAULT 0,
  "net_profit" DECIMAL(18,4) NOT NULL,
  "margin_percent" DECIMAL(9,4) NOT NULL,
  "incomplete_cost" BOOLEAN NOT NULL DEFAULT false,
  "calculated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "profit_snapshots_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "cost_price_history_internal_sku_id_effective_from_idx" ON "cost_price_history"("internal_sku_id", "effective_from");
CREATE UNIQUE INDEX "expense_categories_company_id_code_key" ON "expense_categories"("company_id", "code");
CREATE INDEX "expenses_company_id_expense_date_idx" ON "expenses"("company_id", "expense_date");
CREATE INDEX "expenses_store_id_expense_date_idx" ON "expenses"("store_id", "expense_date");
CREATE UNIQUE INDEX "profit_snapshots_order_id_key" ON "profit_snapshots"("order_id");

ALTER TABLE "cost_price_history" ADD CONSTRAINT "cost_price_history_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "expense_categories" ADD CONSTRAINT "expense_categories_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "expenses" ADD CONSTRAINT "expenses_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "expenses" ADD CONSTRAINT "expenses_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "expenses" ADD CONSTRAINT "expenses_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "expenses" ADD CONSTRAINT "expenses_category_id_fkey" FOREIGN KEY ("category_id") REFERENCES "expense_categories"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
ALTER TABLE "profit_snapshots" ADD CONSTRAINT "profit_snapshots_order_id_fkey" FOREIGN KEY ("order_id") REFERENCES "orders"("id") ON DELETE CASCADE ON UPDATE CASCADE;
