package com.company.erp.platform.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.erp.audit.AuditService;
import com.company.erp.identity.AuthController;
import com.company.erp.identity.IdentityService;
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

@WebMvcTest(value = AuthController.class, properties = "app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class SecurityConfigTest {
    @Autowired MockMvc mvc;
    @MockitoBean IdentityService identities;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void allowRequestsThroughTheRateLimiter() {
        ValueOperations<String, String> values = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(values);
        org.mockito.Mockito.when(values.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRejectsMissingCsrf() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").with(oidcLogin())).andExpect(status().isForbidden());
    }
}
