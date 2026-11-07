package com.company.erp.company;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("companyAccess")
public class CompanyAccess {
    private final JdbcClient jdbc;

    public CompanyAccess(JdbcClient jdbc) { this.jdbc = jdbc; }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        if (authentication == null || !authentication.isAuthenticated() || companyId == null) return false;
        boolean superAdmin = authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals("super-admin") || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean permitted = superAdmin || authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission));
        if (!permitted) return false;
        if (superAdmin) return true;
        String subject = authentication.getName();
        return jdbc.sql("select count(*) from company_memberships where company_id = :companyId and keycloak_subject = :subject")
            .param("companyId", companyId.toString()).param("subject", subject).query(Integer.class).single() > 0;
    }
}
