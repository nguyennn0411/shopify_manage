package com.company.erp.pm;

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

@WebMvcTest(value = PmController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class PmSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Autowired MockMvc mvc;
    @MockitoBean PmService pmService;
    @MockitoBean(name = "pmAccess") PmAccess pmAccess;
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
        when(pmService.projects(COMPANY_ID)).thenReturn(List.of());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("pm.project.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("pm.project.write"))).thenReturn(false);
        when(pmAccess.canViewTimesheets(any(), eq(COMPANY_ID), eq(EMPLOYEE_ID))).thenReturn(false);
    }

    @Test
    void projectWriteRequiresSeparatePermission() throws Exception {
        mvc.perform(get("/api/v1/pm/projects").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pm/projects").param("companyId", COMPANY_ID.toString())
                .contentType("application/json").content("{\"name\":\"Demo\",\"status\":\"PLANNED\"}").with(oidcLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void timesheetsRequireAllOrOwnTimesheetPermission() throws Exception {
        mvc.perform(get("/api/v1/pm/timesheets").param("companyId", COMPANY_ID.toString())
                .param("employeeId", EMPLOYEE_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(pmAccess.canViewTimesheets(any(), eq(COMPANY_ID), eq(EMPLOYEE_ID))).thenReturn(true);
        when(pmService.timesheets(eq(COMPANY_ID), eq(EMPLOYEE_ID), any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/pm/timesheets").param("companyId", COMPANY_ID.toString())
                .param("employeeId", EMPLOYEE_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void summaryRequiresPmAccess() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("pm.access"))).thenReturn(false);
        mvc.perform(get("/api/v1/pm/summary").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("pm.access"))).thenReturn(true);
        when(pmService.summary(eq(COMPANY_ID), any())).thenReturn(null);
        mvc.perform(get("/api/v1/pm/summary").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }
}
