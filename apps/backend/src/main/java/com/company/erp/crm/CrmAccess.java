package com.company.erp.crm;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("crmAccess")
public class CrmAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public CrmAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        return companyAccess.canAccess(authentication, companyId, permission);
    }

    public boolean canAccessCustomer(Authentication authentication, UUID companyId, UUID customerId, String permission) {
        if (!companyAccess.canAccess(authentication, companyId, permission) || customerId == null) return false;
        if (hasGlobalAccess(authentication, companyId, permission)) return exists("customers", companyId, customerId);
        return jdbc.sql("""
            select count(*) from customers c
            left join employees e on e.id=c.owner_employee_id and e.company_id=c.company_id
            left join departments d on d.id=e.department_id and d.company_id=e.company_id
            where c.company_id=:companyId and c.id=:id and (
              e.keycloak_subject=:subject
              or exists (select 1 from company_membership_scopes s
                join company_memberships m on m.company_id=s.company_id and m.keycloak_subject=s.keycloak_subject
                where s.company_id=c.company_id and s.keycloak_subject=:subject and m.status='ACTIVE' and (
                  (s.scope_type='SELF' and s.scope_id=e.id)
                  or (s.scope_type='TEAM' and (s.scope_id=e.id or s.scope_id=e.manager_employee_id))
                  or (s.scope_type='DEPARTMENT' and s.scope_id=e.department_id)
                  or (s.scope_type='BRANCH' and (s.scope_id=e.branch_id or s.scope_id=d.branch_id))
                ))
            )
            """).param("companyId", companyId.toString()).param("id", customerId.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    public boolean canAccessLead(Authentication authentication, UUID companyId, UUID leadId, String permission) {
        return canAccessOwned(authentication, companyId, "crm_leads", leadId, permission);
    }

    public boolean canAccessOpportunity(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessCustomerRecord(authentication, companyId, "crm_opportunities", id, permission);
    }

    public boolean canAccessQuote(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessCustomerRecord(authentication, companyId, "crm_quotes", id, permission);
    }

    public boolean canAccessContract(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessCustomerRecord(authentication, companyId, "crm_contracts", id, permission);
    }

    public boolean canAccessOrder(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessCustomerRecord(authentication, companyId, "crm_sales_orders", id, permission);
    }

    public boolean canAccessTicket(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessCustomerRecord(authentication, companyId, "crm_support_tickets", id, permission);
    }

    public boolean canAccessCampaign(Authentication authentication, UUID companyId, UUID id, String permission) {
        return canAccessOwned(authentication, companyId, "crm_campaigns", id, permission);
    }

    private boolean canAccessCustomerRecord(Authentication authentication, UUID companyId, String table,
            UUID id, String permission) {
        if (!allowedCustomerTable(table) || !companyAccess.canAccess(authentication, companyId, permission) || id == null) return false;
        UUID customerId = jdbc.sql("select customer_id from " + table + " where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", id.toString())
            .query(String.class).optional().map(String::trim).map(UUID::fromString).orElse(null);
        return canAccessCustomer(authentication, companyId, customerId, permission);
    }

    private boolean canAccessOwned(Authentication authentication, UUID companyId, String table,
            UUID id, String permission) {
        if (!allowedOwnedTable(table) || !companyAccess.canAccess(authentication, companyId, permission) || id == null) return false;
        if (hasGlobalAccess(authentication, companyId, permission)) return exists(table, companyId, id);
        return jdbc.sql("""
            select count(*) from %s r
            left join employees e on e.id=r.owner_employee_id and e.company_id=r.company_id
            left join departments d on d.id=e.department_id and d.company_id=e.company_id
            where r.company_id=:companyId and r.id=:id and (
              e.keycloak_subject=:subject
              or exists (select 1 from company_membership_scopes s
                join company_memberships m on m.company_id=s.company_id and m.keycloak_subject=s.keycloak_subject
                where s.company_id=r.company_id and s.keycloak_subject=:subject and m.status='ACTIVE' and (
                  (s.scope_type='SELF' and s.scope_id=e.id)
                  or (s.scope_type='TEAM' and (s.scope_id=e.id or s.scope_id=e.manager_employee_id))
                  or (s.scope_type='DEPARTMENT' and s.scope_id=e.department_id)
                  or (s.scope_type='BRANCH' and (s.scope_id=e.branch_id or s.scope_id=d.branch_id))
                ))
            )
            """.formatted(table)).param("companyId", companyId.toString()).param("id", id.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
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

    private static boolean allowedCustomerTable(String table) {
        return table.equals("crm_opportunities") || table.equals("crm_quotes") || table.equals("crm_contracts")
            || table.equals("crm_sales_orders") || table.equals("crm_support_tickets");
    }

    private static boolean allowedOwnedTable(String table) {
        return table.equals("crm_leads") || table.equals("crm_campaigns");
    }
}
