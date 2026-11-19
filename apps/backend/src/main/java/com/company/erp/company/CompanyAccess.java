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
        if (superAdmin) return true;
        boolean identityPermission = authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals(permission));
        String subject = authentication.getName();
        return jdbc.sql("""
            select count(distinct m.keycloak_subject)
            from company_memberships m
            left join company_membership_roles mr
              on mr.company_id=m.company_id and mr.keycloak_subject=m.keycloak_subject
            left join company_role_permissions rp on rp.role_id=mr.role_id
            where m.company_id=:companyId and m.keycloak_subject=:subject and m.status='ACTIVE'
              and (:identityPermission=true or rp.permission_code=:permission)
            """).param("companyId", companyId.toString()).param("subject", subject)
            .param("identityPermission", identityPermission).param("permission", permission)
            .query(Integer.class).single() > 0;
    }

    public boolean hasScope(Authentication authentication, UUID companyId, String scopeType, UUID scopeId) {
        if (authentication == null || !authentication.isAuthenticated() || companyId == null || scopeId == null) return false;
        if (authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("super-admin") || authority.getAuthority().equals("ROLE_SUPER_ADMIN"))) return true;
        return jdbc.sql("""
            select count(*) from company_membership_scopes s
            join company_memberships m on m.company_id=s.company_id and m.keycloak_subject=s.keycloak_subject
            where s.company_id=:companyId and s.keycloak_subject=:subject and m.status='ACTIVE'
              and ((s.scope_type='COMPANY' and s.scope_id=:companyId)
                or (s.scope_type=:scopeType and s.scope_id=:scopeId))
            """).param("companyId", companyId.toString()).param("subject", authentication.getName())
            .param("scopeType", scopeType).param("scopeId", scopeId.toString())
            .query(Integer.class).single() > 0;
    }
}
