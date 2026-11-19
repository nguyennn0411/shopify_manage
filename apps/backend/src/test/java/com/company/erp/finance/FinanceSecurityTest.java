package com.company.erp.finance;

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

@WebMvcTest(value = FinanceController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class FinanceSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROJECT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockitoBean FinanceService financeService;
    @MockitoBean(name="financeAccess") FinanceAccess financeAccess;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        ValueOperations<String,String> values = org.mockito.Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(any(String.class))).thenReturn(1L);
    }

    @Test
    void summaryRequiresFinanceAccess() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("finance.access"))).thenReturn(false);
        mvc.perform(get("/api/v1/finance/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("finance.access"))).thenReturn(true);
        mvc.perform(get("/api/v1/finance/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void transactionWriteIsSeparateFromRead() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("finance.transaction.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("finance.transaction.write"))).thenReturn(false);
        when(financeService.transactions(eq(COMPANY_ID),any(),any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/finance/transactions").param("companyId",COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/finance/transactions").param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"transactionType\":\"PAYMENT\",\"categoryId\":\"22222222-2222-2222-2222-222222222222\",\"amount\":1000,\"currency\":\"VND\",\"occurredOn\":\"2026-11-13\",\"description\":\"Test\"}")
            .with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void transactionApprovalRequiresObjectScopeAndApprovalPermission() throws Exception {
        when(financeAccess.canAccessRecord(any(),eq(COMPANY_ID),eq("finance_cash_transactions"),eq(TRANSACTION_ID),eq("finance.transaction.approve"))).thenReturn(false);
        mvc.perform(post("/api/v1/finance/transactions/{id}/decision",TRANSACTION_ID).param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf()))
            .andExpect(status().isForbidden());
        when(financeAccess.canAccessRecord(any(),any(),any(),any(),any())).thenReturn(true);
        mvc.perform(post("/api/v1/finance/transactions/{id}/decision",TRANSACTION_ID).param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    void projectCostRequiresProjectScope() throws Exception {
        when(financeAccess.canAccessProject(any(),eq(COMPANY_ID),eq(PROJECT_ID),eq("finance.project-cost.write"))).thenReturn(false);
        mvc.perform(post("/api/v1/finance/project-costs").param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"projectId\":\""+PROJECT_ID+"\",\"categoryId\":\"22222222-2222-2222-2222-222222222222\",\"costType\":\"SERVICE\",\"amount\":1000,\"currency\":\"VND\",\"incurredOn\":\"2026-11-13\",\"description\":\"Test\"}")
            .with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
    }
}
