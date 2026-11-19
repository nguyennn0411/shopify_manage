package com.company.erp.operations;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("operationsAccess")
public class OperationsAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public OperationsAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        return companyAccess.canAccess(authentication, companyId, permission);
    }

    public boolean canAccessPurchaseRequest(Authentication authentication, UUID companyId, UUID id, String permission) {
        if (id == null || !companyAccess.canAccess(authentication, companyId, permission)) return false;
        if (hasGlobal(authentication, companyId, permission)) return exists("purchase_requests", companyId, id);
        return jdbc.sql("""
            select count(*) from purchase_requests r where r.company_id=:companyId and r.id=:id and (
              r.requester_subject=:subject
              or (r.department_id is not null and exists(select 1 from company_membership_scopes s
                where s.company_id=r.company_id and s.keycloak_subject=:subject and s.scope_type='DEPARTMENT' and s.scope_id=r.department_id))
              or (r.project_id is not null and exists(select 1 from company_membership_scopes s
                where s.company_id=r.company_id and s.keycloak_subject=:subject and s.scope_type='PROJECT' and s.scope_id=r.project_id))
            )
            """).param("companyId", companyId.toString()).param("id", id.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    public boolean canAccessAsset(Authentication authentication, UUID companyId, UUID id, String permission) {
        if (id == null || !companyAccess.canAccess(authentication, companyId, permission)) return false;
        if (hasGlobal(authentication, companyId, permission)) return exists("ops_assets", companyId, id);
        return jdbc.sql("""
            select count(*) from ops_assets a left join employees e on e.id=a.custodian_employee_id
            where a.company_id=:companyId and a.id=:id and (
              e.keycloak_subject=:subject
              or (a.department_id is not null and exists(select 1 from company_membership_scopes s
                where s.company_id=a.company_id and s.keycloak_subject=:subject and s.scope_type='DEPARTMENT' and s.scope_id=a.department_id))
            )
            """).param("companyId", companyId.toString()).param("id", id.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    public boolean hasGlobal(Authentication authentication, UUID companyId, String permission) {
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
