package com.company.erp.crm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

@WebMvcTest(value = CrmController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class CrmSecurityTest {
    private static final UUID COMPANY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID QUOTE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired MockMvc mvc;
    @MockitoBean CrmService crmService;
    @MockitoBean(name="crmAccess") CrmAccess crmAccess;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
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
    void summaryRequiresCrmAccess() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("crm.access"))).thenReturn(false);
        mvc.perform(get("/api/v1/crm/summary").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("crm.access"))).thenReturn(true);
        mvc.perform(get("/api/v1/crm/summary").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void customerWriteIsSeparateFromRead() throws Exception {
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("crm.customer.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(), eq(COMPANY_ID), eq("crm.customer.write"))).thenReturn(false);
        when(crmService.customers(eq(COMPANY_ID), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/crm/customers").param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/crm/customers").param("companyId", COMPANY_ID.toString())
                .contentType("application/json")
                .content("{\"displayName\":\"Acme\",\"customerType\":\"ORGANIZATION\"}")
                .with(oidcLogin()).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerDetailsRequireAssignedScope() throws Exception {
        when(crmAccess.canAccessCustomer(any(), eq(COMPANY_ID), eq(CUSTOMER_ID), eq("crm.customer.read"))).thenReturn(false);
        mvc.perform(get("/api/v1/crm/customers/{id}/contacts", CUSTOMER_ID)
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isForbidden());
        when(crmAccess.canAccessCustomer(any(), eq(COMPANY_ID), eq(CUSTOMER_ID), eq("crm.customer.read"))).thenReturn(true);
        when(crmService.contacts(COMPANY_ID, CUSTOMER_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/crm/customers/{id}/contacts", CUSTOMER_ID)
                .param("companyId", COMPANY_ID.toString()).with(oidcLogin()))
            .andExpect(status().isOk());
    }

    @Test
    void quoteApprovalRequiresApprovalPermission() throws Exception {
        when(crmAccess.canAccessQuote(any(), eq(COMPANY_ID), eq(QUOTE_ID), eq("crm.quote.approve"))).thenReturn(false);
        mvc.perform(post("/api/v1/crm/quotes/{id}/decision", QUOTE_ID)
                .param("companyId", COMPANY_ID.toString()).contentType("application/json")
                .content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf()))
            .andExpect(status().isForbidden());
        when(crmAccess.canAccessQuote(any(), any(), any(), any())).thenReturn(true);
        mvc.perform(post("/api/v1/crm/quotes/{id}/decision", QUOTE_ID)
                .param("companyId", COMPANY_ID.toString()).contentType("application/json")
                .content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf()))
            .andExpect(status().isOk());
    }
}
