package com.company.erp.hr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class HrSensitiveDataSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RUN_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
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
    }

    @Test
    void payslipsRequireAllPayrollPermissionOrOwnMatch() throws Exception {
        when(hrAccess.canViewPayslips(any(), eq(COMPANY_ID), eq(EMPLOYEE_ID))).thenReturn(false);
        mvc.perform(get("/api/v1/hr/payroll/payslips").param("companyId", COMPANY_ID.toString()).param("employeeId", EMPLOYEE_ID.toString())
                .with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(hrAccess.canViewPayslips(any(), eq(COMPANY_ID), eq(EMPLOYEE_ID))).thenReturn(true);
        when(hrService.payslips(COMPANY_ID, EMPLOYEE_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/hr/payroll/payslips").param("companyId", COMPANY_ID.toString()).param("employeeId", EMPLOYEE_ID.toString())
                .with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void payrollRunsRequireCompanyWideReadPermission() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.payroll.read.all"))).thenReturn(false);
        mvc.perform(get("/api/v1/hr/payroll/runs").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.payroll.read.all"))).thenReturn(true);
        when(hrService.payrollRuns(COMPANY_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/hr/payroll/runs").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void salaryStructuresAreNotReadableWithoutAllPayrollPermission() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.payroll.read.all"))).thenReturn(false);
        mvc.perform(get("/api/v1/hr/payroll/salary-structures/" + EMPLOYEE_ID).param("companyId", COMPANY_ID.toString())
                .with(oidcLogin()))
            .andExpect(status().isForbidden());
    }

    @Test
    void employeeDocumentsRequireSensitiveProfilePermission() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.read.sensitive"))).thenReturn(false);
        mvc.perform(get("/api/v1/hr/employees/" + EMPLOYEE_ID + "/documents").param("companyId", COMPANY_ID.toString())
                .with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.employee.read.sensitive"))).thenReturn(true);
        when(hrService.employeeDocuments(COMPANY_ID, EMPLOYEE_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/hr/employees/" + EMPLOYEE_ID + "/documents").param("companyId", COMPANY_ID.toString())
                .with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void adjustmentReviewRequiresAttendanceApprovePermission() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.attendance.approve"))).thenReturn(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/hr/attendance/adjustments/" + RUN_ID + "/review")
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVED\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void payslipPublishRequiresPayrollWritePermission() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("hr.payroll.write"))).thenReturn(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/hr/payroll/runs/" + RUN_ID + "/publish-payslips")
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()).with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"currency\":\"VND\"}"))
            .andExpect(status().isForbidden());
    }
}
