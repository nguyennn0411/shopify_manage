package com.company.erp.hr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@WebMvcTest(value = HrController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class HrSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Autowired MockMvc mvc;
    @MockitoBean HrService hrService;
    @MockitoBean(name = "hrAccess") HrAccess hrAccess;
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
        when(hrService.employees(eq(COMPANY_ID), any())).thenReturn(List.of());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.read.basic"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.read.sensitive"))).thenReturn(false);
    }

    @Test
    void basicProfilePermissionDoesNotGrantPrivateProfile() throws Exception {
        mvc.perform(get("/api/v1/hr/employees").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/hr/employees/{id}/private", EMPLOYEE_ID).param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void basicEmployeeWriteDoesNotGrantSensitiveProfileWrite() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.write"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.write.sensitive"))).thenReturn(false);

        mvc.perform(patch("/api/v1/hr/employees/{id}", EMPLOYEE_ID)
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Nguyen Van A\"}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/hr/employees/{id}/private", EMPLOYEE_ID)
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"identityNumber\":\"0123456789\"}"))
            .andExpect(status().isForbidden());
    }
}
