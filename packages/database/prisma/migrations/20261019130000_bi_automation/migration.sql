CREATE TYPE "MarketingPlatform" AS ENUM ('META_ADS', 'GOOGLE_ADS', 'TIKTOK_ADS');
CREATE TYPE "IntegrationStatus" AS ENUM ('ACTIVE', 'ERROR', 'DISABLED');
CREATE TYPE "AlertType" AS ENUM ('LOW_STOCK', 'LOW_MARGIN', 'SYNC_FAILURE', 'REVENUE_DROP');
CREATE TYPE "AlertEventStatus" AS ENUM ('OPEN', 'ACKNOWLEDGED', 'RESOLVED');

CREATE TABLE "daily_metrics" (
  "id" UUID NOT NULL, "store_id" UUID NOT NULL, "metric_date" DATE NOT NULL, "currency" TEXT NOT NULL,
  "orders" INTEGER NOT NULL, "units" INTEGER NOT NULL, "gross_revenue" DECIMAL(18,4) NOT NULL,
  "net_revenue" DECIMAL(18,4) NOT NULL, "refunds" DECIMAL(18,4) NOT NULL, "cogs" DECIMAL(18,4) NOT NULL,
  "net_profit" DECIMAL(18,4) NOT NULL, "calculated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "daily_metrics_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "marketing_connections" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "store_id" UUID, "platform" "MarketingPlatform" NOT NULL,
  "account_id" TEXT NOT NULL, "name" TEXT NOT NULL, "currency" TEXT NOT NULL, "encrypted_credentials" TEXT NOT NULL,
  "status" "IntegrationStatus" NOT NULL DEFAULT 'ACTIVE', "last_sync_at" TIMESTAMP(3), "last_error" TEXT,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "marketing_connections_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "marketing_daily_metrics" (
  "id" UUID NOT NULL, "connection_id" UUID NOT NULL, "metric_date" DATE NOT NULL, "campaign_id" TEXT NOT NULL,
  "campaign_name" TEXT NOT NULL, "currency" TEXT NOT NULL, "spend" DECIMAL(18,4) NOT NULL,
  "impressions" BIGINT NOT NULL DEFAULT 0, "clicks" BIGINT NOT NULL DEFAULT 0, "conversions" DECIMAL(18,4) NOT NULL DEFAULT 0,
  "revenue" DECIMAL(18,4) NOT NULL DEFAULT 0, "synced_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "marketing_daily_metrics_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "forecast_snapshots" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "internal_sku_id" UUID NOT NULL,
  "generated_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "horizon_days" INTEGER NOT NULL, "sample_days" INTEGER NOT NULL,
  "method" TEXT NOT NULL, "predicted_units" JSONB NOT NULL, "mean_absolute_error" DECIMAL(18,4),
  CONSTRAINT "forecast_snapshots_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "alert_rules" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "store_id" UUID, "name" TEXT NOT NULL, "type" "AlertType" NOT NULL,
  "threshold" DECIMAL(18,4) NOT NULL, "window_days" INTEGER NOT NULL DEFAULT 7, "enabled" BOOLEAN NOT NULL DEFAULT true,
  "channels" JSONB NOT NULL, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "alert_rules_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "alert_events" (
  "id" UUID NOT NULL, "rule_id" UUID NOT NULL, "fingerprint" TEXT NOT NULL, "severity" TEXT NOT NULL, "message" TEXT NOT NULL,
  "payload" JSONB, "status" "AlertEventStatus" NOT NULL DEFAULT 'OPEN', "detected_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "acknowledged_at" TIMESTAMP(3), "acknowledged_by_id" UUID, "resolved_at" TIMESTAMP(3),
  CONSTRAINT "alert_events_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "daily_metrics_store_id_metric_date_currency_key" ON "daily_metrics"("store_id", "metric_date", "currency");
CREATE INDEX "daily_metrics_metric_date_idx" ON "daily_metrics"("metric_date");
CREATE UNIQUE INDEX "marketing_connections_company_id_platform_account_id_key" ON "marketing_connections"("company_id", "platform", "account_id");
CREATE INDEX "marketing_connections_company_id_status_idx" ON "marketing_connections"("company_id", "status");
CREATE UNIQUE INDEX "marketing_daily_metrics_connection_id_metric_date_campaign_id_key" ON "marketing_daily_metrics"("connection_id", "metric_date", "campaign_id");
CREATE INDEX "marketing_daily_metrics_metric_date_idx" ON "marketing_daily_metrics"("metric_date");
CREATE INDEX "forecast_snapshots_company_id_generated_at_idx" ON "forecast_snapshots"("company_id", "generated_at");
CREATE INDEX "forecast_snapshots_internal_sku_id_generated_at_idx" ON "forecast_snapshots"("internal_sku_id", "generated_at");
CREATE INDEX "alert_rules_company_id_enabled_idx" ON "alert_rules"("company_id", "enabled");
CREATE UNIQUE INDEX "alert_events_rule_id_fingerprint_key" ON "alert_events"("rule_id", "fingerprint");
CREATE INDEX "alert_events_status_detected_at_idx" ON "alert_events"("status", "detected_at");

ALTER TABLE "daily_metrics" ADD CONSTRAINT "daily_metrics_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "marketing_connections" ADD CONSTRAINT "marketing_connections_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "marketing_connections" ADD CONSTRAINT "marketing_connections_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "marketing_daily_metrics" ADD CONSTRAINT "marketing_daily_metrics_connection_id_fkey" FOREIGN KEY ("connection_id") REFERENCES "marketing_connections"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "forecast_snapshots" ADD CONSTRAINT "forecast_snapshots_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "forecast_snapshots" ADD CONSTRAINT "forecast_snapshots_internal_sku_id_fkey" FOREIGN KEY ("internal_sku_id") REFERENCES "internal_skus"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "alert_rules" ADD CONSTRAINT "alert_rules_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "alert_rules" ADD CONSTRAINT "alert_rules_store_id_fkey" FOREIGN KEY ("store_id") REFERENCES "stores"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "alert_events" ADD CONSTRAINT "alert_events_rule_id_fkey" FOREIGN KEY ("rule_id") REFERENCES "alert_rules"("id") ON DELETE CASCADE ON UPDATE CASCADE;

INSERT INTO "permissions" ("code", "description") VALUES
  ('analytics.read', 'Xem BI và cảnh báo'),
  ('analytics.write', 'Quản trị BI, tích hợp và tự động hóa')
ON CONFLICT ("code") DO UPDATE SET "description" = EXCLUDED."description";
INSERT INTO "role_permissions" ("role_id", "permission_code")
SELECT "id", 'analytics.read' FROM "roles" WHERE "name" IN ('Super Admin', 'CEO', 'Operations Manager', 'Marketing')
ON CONFLICT DO NOTHING;
INSERT INTO "role_permissions" ("role_id", "permission_code")
SELECT "id", 'analytics.write' FROM "roles" WHERE "name" IN ('Super Admin', 'Operations Manager', 'Marketing')
ON CONFLICT DO NOTHING;
