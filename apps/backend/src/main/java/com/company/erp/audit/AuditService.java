package com.company.erp.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void authenticationEvent(Authentication authentication, String action, HttpServletRequest request) {
        if (authentication == null) return;
        write(null, authentication.getName(), action, "SESSION", authentication.getName(),
            "{\"ip\":\"" + safe(request.getRemoteAddr()) + "\"}");
    }

    public void write(UUID companyId, String actorSubject, String action, String resourceType, String resourceId, String metadata) {
        jdbc.sql("insert into audit_logs(id, company_id, actor_subject, action, resource_type, resource_id, metadata, created_at) values(:id,:companyId,:actor,:action,:type,:resource,:metadata,current_timestamp)")
            .param("id", UUID.randomUUID().toString())
            .param("companyId", companyId == null ? null : companyId.toString())
            .param("actor", actorSubject).param("action", action).param("type", resourceType)
            .param("resource", resourceId).param("metadata", metadata).update();
    }

    private static String safe(String value) { return value == null ? "unknown" : value.replace("\\", "").replace("\"", ""); }
}
