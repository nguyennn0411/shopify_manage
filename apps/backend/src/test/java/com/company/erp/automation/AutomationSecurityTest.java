package com.company.erp.automation;

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

@WebMvcTest(value=AutomationController.class,properties="app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class AutomationSecurityTest {
    private static final UUID COMPANY_ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Autowired MockMvc mvc;
    @MockitoBean AutomationService service;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach @SuppressWarnings("unchecked") void setup(){ValueOperations<String,String>values=org.mockito.Mockito.mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.increment(any(String.class))).thenReturn(1L);}

    @Test void summaryRequiresAutomationAccess() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.access"))).thenReturn(false);mvc.perform(get("/api/v1/automation/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.access"))).thenReturn(true);when(service.summary(COMPANY_ID)).thenReturn(new AutomationModels.AutomationSummary(0,0,0,0,0));mvc.perform(get("/api/v1/automation/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());}
    @Test void workflowReadDoesNotGrantWrite() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.read"))).thenReturn(true);when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.write"))).thenReturn(false);when(service.workflows(COMPANY_ID)).thenReturn(List.of());mvc.perform(get("/api/v1/automation/workflows").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());mvc.perform(post("/api/v1/automation/workflows").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"code\":\"WF\",\"name\":\"Workflow\",\"triggerType\":\"MANUAL\",\"triggerConfigJson\":\"{}\",\"steps\":[{\"name\":\"Notify\",\"stepType\":\"NOTIFICATION\",\"configJson\":\"{\\\"targetSubject\\\":\\\"user\\\",\\\"title\\\":\\\"Done\\\"}\",\"continueOnError\":false}]}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());}
    @Test void manualRunRequiresDedicatedPermission() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.run"))).thenReturn(false);mvc.perform(post("/api/v1/automation/workflows/{id}/run",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"inputJson\":\"{}\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("automation.run"))).thenReturn(true);when(service.run(eq(COMPANY_ID),eq(RESOURCE_ID),any(),any())).thenReturn(new AutomationModels.CreatedResource(RESOURCE_ID,"SUCCEEDED"));mvc.perform(post("/api/v1/automation/workflows/{id}/run",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"inputJson\":\"{}\"}").with(oidcLogin()).with(csrf())).andExpect(status().isOk());}
    @Test void webhookWriteIsSeparateFromIntegrationRead() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("integration.read"))).thenReturn(true);when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("webhook.write"))).thenReturn(false);when(service.connections(COMPANY_ID)).thenReturn(List.of());mvc.perform(get("/api/v1/automation/connections").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());mvc.perform(post("/api/v1/automation/webhooks").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"code\":\"HOOK\",\"name\":\"Webhook\",\"eventType\":\"order.created\",\"targetUrl\":\"https://example.com/hook\",\"timeoutSeconds\":10,\"maxAttempts\":5}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());}
}
