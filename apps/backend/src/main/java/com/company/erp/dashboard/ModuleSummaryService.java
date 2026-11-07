package com.company.erp.dashboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class ModuleSummaryService {
    private static final Map<String, String> TABLES = Map.ofEntries(
        Map.entry("stores", "stores"), Map.entry("products", "products"), Map.entry("orders", "orders"),
        Map.entry("suppliers", "suppliers"), Map.entry("procurement", "purchase_requests"),
        Map.entry("pim", "pim_profiles"), Map.entry("fulfillment", "fulfillment_jobs"),
        Map.entry("warehouses", "warehouses"), Map.entry("audit", "audit_logs"),
        Map.entry("projects", "pm_projects"), Map.entry("tasks", "pm_tasks"));
    private final JdbcClient jdbc;

    public ModuleSummaryService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Map<String, Long> counts(UUID companyId) {
        Map<String, Long> result = new LinkedHashMap<>();
        TABLES.forEach((module, table) -> result.put(module,
            jdbc.sql("select count(*) from " + table + " where company_id=:companyId")
                .param("companyId", companyId.toString()).query(Long.class).single()));
        return result;
    }
}
