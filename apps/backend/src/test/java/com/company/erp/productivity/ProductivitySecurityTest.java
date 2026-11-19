package com.company.erp.productivity;

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

@WebMvcTest(value=ProductivityController.class,properties="app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class ProductivitySecurityTest {
    private static final UUID COMPANY_ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    @Autowired MockMvc mvc;
    @MockitoBean ProductivityService productivityService;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;
    @BeforeEach @SuppressWarnings("unchecked") void setup(){ValueOperations<String,String> values=org.mockito.Mockito.mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.increment(any(String.class))).thenReturn(1L);}

    @Test void summaryRequiresPlatformSearch() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("platform.search"))).thenReturn(false);mvc.perform(get("/api/v1/productivity/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("platform.search"))).thenReturn(true);mvc.perform(get("/api/v1/productivity/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());}
    @Test void searchValidatesPermissionAndQuery() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("platform.search"))).thenReturn(true);when(productivityService.search(eq(COMPANY_ID),eq("policy"),any(),any())).thenReturn(List.of());mvc.perform(get("/api/v1/productivity/search").param("companyId",COMPANY_ID.toString()).param("q","policy").with(oidcLogin())).andExpect(status().isOk());mvc.perform(get("/api/v1/productivity/search").param("companyId",COMPANY_ID.toString()).param("q","x").with(oidcLogin())).andExpect(status().isBadRequest());}
    @Test void notificationReadDoesNotGrantWrite() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("notification.read"))).thenReturn(true);when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("notification.write"))).thenReturn(false);when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("notification.manage"))).thenReturn(false);mvc.perform(get("/api/v1/productivity/notifications").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());mvc.perform(post("/api/v1/productivity/notifications").param("companyId",COMPANY_ID.toString()).contentType("application/json").content("{\"title\":\"Nhắc việc\",\"severity\":\"INFO\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());}
    @Test void auditRequiresDedicatedPermission() throws Exception {when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("platform.search"))).thenReturn(true);when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("audit.read"))).thenReturn(false);mvc.perform(get("/api/v1/productivity/audit").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("audit.read"))).thenReturn(true);when(productivityService.auditLogs(eq(COMPANY_ID),any())).thenReturn(List.of());mvc.perform(get("/api/v1/productivity/audit").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());}
}
