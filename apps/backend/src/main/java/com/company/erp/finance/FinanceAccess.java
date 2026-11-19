package com.company.erp.finance;

import com.company.erp.company.CompanyAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("financeAccess")
public class FinanceAccess {
    private static final Set<String> SCOPED_TABLES = Set.of(
        "finance_budgets", "finance_cash_transactions", "finance_invoices", "finance_advances");
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public FinanceAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        return companyAccess.canAccess(authentication, companyId, permission);
    }

    public boolean canAccessRecord(Authentication authentication, UUID companyId, String table,
            UUID id, String permission) {
        if (!SCOPED_TABLES.contains(table) || id == null
                || !companyAccess.canAccess(authentication, companyId, permission)) return false;
        if (hasGlobalAccess(authentication, companyId, permission)) return exists(table, companyId, id);
        return jdbc.sql("""
            select count(*) from %s r where r.company_id=:companyId and r.id=:id and (
              (r.department_id is not null and exists(select 1 from company_membership_scopes s
                join company_memberships m on m.company_id=s.company_id and m.keycloak_subject=s.keycloak_subject
                where s.company_id=r.company_id and s.keycloak_subject=:subject and m.status='ACTIVE'
                  and s.scope_type='DEPARTMENT' and s.scope_id=r.department_id))
              or (r.project_id is not null and exists(select 1 from company_membership_scopes s
                join company_memberships m on m.company_id=s.company_id and m.keycloak_subject=s.keycloak_subject
                where s.company_id=r.company_id and s.keycloak_subject=:subject and m.status='ACTIVE'
                  and s.scope_type='PROJECT' and s.scope_id=r.project_id))
            )
            """.formatted(table)).param("companyId", companyId.toString()).param("id", id.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    public boolean canAccessProject(Authentication authentication, UUID companyId, UUID projectId, String permission) {
        if (projectId == null || !companyAccess.canAccess(authentication, companyId, permission)) return false;
        return hasGlobalAccess(authentication, companyId, permission)
            || companyAccess.hasScope(authentication, companyId, "PROJECT", projectId);
    }

    private boolean hasGlobalAccess(Authentication authentication, UUID companyId, String permission) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals(permission) || authority.getAuthority().equals("super-admin")
                || authority.getAuthority().equals("ROLE_SUPER_ADMIN"))
            || companyAccess.hasScope(authentication, companyId, "COMPANY", companyId);
    }

    private boolean exists(String table, UUID companyId, UUID id) {
        return jdbc.sql("select count(*) from " + table + " where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", id.toString())
            .query(Integer.class).single() > 0;
    }
}
