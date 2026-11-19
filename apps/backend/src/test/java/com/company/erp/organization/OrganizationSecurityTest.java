package com.company.erp.organization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.audit.AuditService;
import com.company.erp.company.CompanyAccess;
import com.company.erp.platform.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = OrganizationController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class OrganizationSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Autowired MockMvc mvc;
    @MockitoBean OrganizationService organization;
    @MockitoBean(name = "companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(any(String.class))).thenReturn(1L);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("company.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("company.write"))).thenReturn(false);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("identity.role.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("identity.role.write"))).thenReturn(false);
        when(organization.branches(COMPANY_ID)).thenReturn(List.of());
        when(organization.roles(COMPANY_ID)).thenReturn(List.of());
    }

    @Test
    void organizationReadDoesNotGrantWrite() throws Exception {
        mvc.perform(get("/api/v1/organization/branches").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/organization/branches").param("companyId", COMPANY_ID.toString())
                .contentType("application/json").content("{\"code\":\"HN\",\"name\":\"Hà Nội\"}").with(oidcLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void viewingRolesDoesNotGrantRoleAdministration() throws Exception {
        mvc.perform(get("/api/v1/organization/roles").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/organization/roles").param("companyId", COMPANY_ID.toString())
                .contentType("application/json").content("{\"code\":\"CUSTOM\",\"name\":\"Custom\"}").with(oidcLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizationRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/organization/branches").param("companyId", COMPANY_ID.toString()))
            .andExpect(status().isUnauthorized());
    }
}
