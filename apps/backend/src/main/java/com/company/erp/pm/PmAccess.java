package com.company.erp.pm;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("pmAccess")
public class PmAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public PmAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canViewTimesheets(Authentication authentication, UUID companyId, UUID employeeId) {
        if (companyAccess.canAccess(authentication, companyId, "pm.timesheet.read")) return true;
        if (!companyAccess.canAccess(authentication, companyId, "pm.timesheet.read.own")) return false;
        String subject = jdbc.sql("select keycloak_subject from employees where id=:employeeId and company_id=:companyId")
            .param("employeeId", employeeId.toString()).param("companyId", companyId.toString())
            .query(String.class).optional().orElse(null);
        return subject != null && subject.equals(authentication.getName());
    }
}
