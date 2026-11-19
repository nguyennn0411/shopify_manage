package com.company.erp.workplace;

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

@WebMvcTest(value=WorkplaceController.class,properties="app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class WorkplaceSecurityTest {
    private static final UUID COMPANY_ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RESOURCE_ID=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Autowired MockMvc mvc;
    @MockitoBean WorkplaceService workplaceService;
    @MockitoBean(name="workplaceAccess") WorkplaceAccess workplaceAccess;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach @SuppressWarnings("unchecked") void setup(){ValueOperations<String,String> values=org.mockito.Mockito.mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.increment(any(String.class))).thenReturn(1L);}

    @Test void summaryRequiresWorkplaceAccess() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("workplace.access"))).thenReturn(false);
        mvc.perform(get("/api/v1/workplace/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("workplace.access"))).thenReturn(true);
        mvc.perform(get("/api/v1/workplace/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());
    }

    @Test void documentVersionRequiresObjectEditScope() throws Exception {
        when(workplaceAccess.canEditDocument(any(),eq(COMPANY_ID),eq(RESOURCE_ID),eq("document.write"))).thenReturn(false);
        String body="{\"fileName\":\"policy.pdf\",\"objectKey\":\"docs/policy.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":10,\"checksumSha256\":\""+"0".repeat(64)+"\"}";
        mvc.perform(post("/api/v1/workplace/documents/{id}/versions",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content(body).with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
        when(workplaceAccess.canEditDocument(any(),eq(COMPANY_ID),eq(RESOURCE_ID),eq("document.write"))).thenReturn(true);
        mvc.perform(post("/api/v1/workplace/documents/{id}/versions",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content(body).with(oidcLogin()).with(csrf())).andExpect(status().isCreated());
    }

    @Test void approvalDecisionRequiresCurrentApproverAndDecisionPermission() throws Exception {
        when(workplaceAccess.canDecideApproval(any(),eq(COMPANY_ID),eq(RESOURCE_ID),eq("approval.decide"))).thenReturn(false);
        mvc.perform(post("/api/v1/workplace/approvals/{id}/decision",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
        when(workplaceAccess.canDecideApproval(any(),eq(COMPANY_ID),eq(RESOURCE_ID),eq("approval.decide"))).thenReturn(true);
        mvc.perform(post("/api/v1/workplace/approvals/{id}/decision",RESOURCE_ID).param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf())).andExpect(status().isOk());
    }

    @Test void legalWriteIsSeparateFromRead() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("legal.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("legal.write"))).thenReturn(false);
        when(workplaceService.legalRecords(COMPANY_ID,null)).thenReturn(List.of());
        mvc.perform(get("/api/v1/workplace/legal").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/workplace/legal").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"recordType\":\"CONTRACT\",\"title\":\"Hợp đồng A\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
    }
}
