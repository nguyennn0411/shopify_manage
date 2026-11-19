package com.company.erp.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.audit.AuditService;
import com.company.erp.company.CompanyAccess;
import com.company.erp.platform.security.SecurityConfig;
import java.math.BigDecimal;
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

@WebMvcTest(value=ReportingController.class,properties="app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class ReportingSecurityTest {
    private static final UUID COMPANY_ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Autowired MockMvc mvc;
    @MockitoBean ReportingService service;
    @MockitoBean(name="reportingAccess") ReportingAccess reportingAccess;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){ValueOperations<String,String>values=org.mockito.Mockito.mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.increment(any(String.class))).thenReturn(1L);}

    @Test void summaryRequiresReportingRead() throws Exception {when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.read"))).thenReturn(false);mvc.perform(get("/api/v1/reporting/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.read"))).thenReturn(true);when(service.summary(eq(COMPANY_ID),any())).thenReturn(new ReportingModels.ExecutiveSummary("VND",0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0,0,0));mvc.perform(get("/api/v1/reporting/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());}
    @Test void strategyReadDoesNotGrantWrite() throws Exception {when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("strategy.read"))).thenReturn(true);when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("strategy.write"))).thenReturn(false);when(service.objectives(eq(COMPANY_ID),any())).thenReturn(List.of());mvc.perform(get("/api/v1/reporting/objectives").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());mvc.perform(post("/api/v1/reporting/objectives").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"code\":\"OBJ\",\"title\":\"Tăng trưởng\",\"scopeType\":\"COMPANY\",\"scopeId\":\"11111111-1111-1111-1111-111111111111\",\"ownerSubject\":\"user\",\"startDate\":\"2026-01-01\",\"endDate\":\"2026-12-31\",\"keyResults\":[{\"code\":\"KR1\",\"title\":\"Đạt mục tiêu\",\"metricType\":\"PERCENT\",\"baselineValue\":0,\"targetValue\":100,\"currentValue\":0,\"weight\":1}]}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());}
    @Test void snapshotRequiresManagePermission() throws Exception {when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.manage"))).thenReturn(false);mvc.perform(post("/api/v1/reporting/snapshots").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"name\":\"Báo cáo tháng\",\"reportType\":\"EXECUTIVE\",\"periodStart\":\"2026-11-01\",\"periodEnd\":\"2026-11-30\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());}
    @Test void exportRequiresDedicatedPermission() throws Exception {when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.read"))).thenReturn(true);when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.export"))).thenReturn(false);mvc.perform(get("/api/v1/reporting/export").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());when(reportingAccess.canAccess(any(),eq(COMPANY_ID),eq("reporting.export"))).thenReturn(true);when(service.exportCsv(eq(COMPANY_ID),any())).thenReturn("metric,value\r\n");mvc.perform(get("/api/v1/reporting/export").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());}
}
