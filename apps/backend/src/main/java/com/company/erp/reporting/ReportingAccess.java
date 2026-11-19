package com.company.erp.reporting;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("reportingAccess")
public class ReportingAccess {
    private final CompanyAccess companyAccess;

    public ReportingAccess(CompanyAccess companyAccess) { this.companyAccess=companyAccess; }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        return companyAccess.canAccess(authentication,companyId,permission);
    }

    public boolean hasGlobalAccess(Authentication authentication, UUID companyId, String permission) {
        if (!companyAccess.canAccess(authentication,companyId,permission)) return false;
        return authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals(permission) || authority.getAuthority().equals("super-admin")
                || authority.getAuthority().equals("ROLE_SUPER_ADMIN"))
            || companyAccess.hasScope(authentication,companyId,"COMPANY",companyId);
    }

    public boolean canAccessScope(Authentication authentication,UUID companyId,String scopeType,UUID scopeId,String permission) {
        if (!companyAccess.canAccess(authentication,companyId,permission) || scopeId==null) return false;
        if (hasGlobalAccess(authentication,companyId,permission)) return true;
        return !"COMPANY".equals(scopeType) && companyAccess.hasScope(authentication,companyId,scopeType,scopeId);
    }
}
