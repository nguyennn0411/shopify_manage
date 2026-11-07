package com.company.erp.hr;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("hrAccess")
public class HrAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public HrAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canViewPayslips(Authentication authentication, UUID companyId, UUID employeeId) {
        if (companyAccess.canAccess(authentication, companyId, "hr.payroll.read.all")) return true;
        if (!companyAccess.canAccess(authentication, companyId, "hr.payroll.read.own")) return false;
        String subject = jdbc.sql("select keycloak_subject from employees where id=:employeeId and company_id=:companyId")
            .param("employeeId", employeeId.toString()).param("companyId", companyId.toString())
            .query(String.class).optional().orElse(null);
        return subject != null && subject.equals(authentication.getName());
    }
}
