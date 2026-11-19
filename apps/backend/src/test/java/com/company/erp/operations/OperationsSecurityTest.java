package com.company.erp.operations;

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

@WebMvcTest(value=OperationsController.class,properties="app.frontend-origin=http://localhost:8088")
@Import(SecurityConfig.class)
class OperationsSecurityTest {
    private static final UUID COMPANY_ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REQUEST_ID=UUID.fromString("22222222-2222-2222-2222-222222222222");
    @Autowired MockMvc mvc;
    @MockitoBean OperationsService operationsService;
    @MockitoBean(name="operationsAccess") OperationsAccess operationsAccess;
    @MockitoBean(name="companyAccess") CompanyAccess companyAccess;
    @MockitoBean AuditService auditService;
    @MockitoBean ClientRegistrationRepository registrations;
    @MockitoBean StringRedisTemplate redis;

    @BeforeEach @SuppressWarnings("unchecked") void setup(){ValueOperations<String,String> values=org.mockito.Mockito.mock(ValueOperations.class);when(redis.opsForValue()).thenReturn(values);when(values.increment(any(String.class))).thenReturn(1L);}

    @Test void summaryRequiresOperationsAccess() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("operations.access"))).thenReturn(false);
        mvc.perform(get("/api/v1/operations/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isForbidden());
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("operations.access"))).thenReturn(true);
        mvc.perform(get("/api/v1/operations/summary").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());
    }

    @Test void supplierWriteIsSeparateFromRead() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("supplier.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("supplier.write"))).thenReturn(false);
        when(operationsService.suppliers(eq(COMPANY_ID),any())).thenReturn(List.of());
        mvc.perform(get("/api/v1/operations/suppliers").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/operations/suppliers").param("companyId",COMPANY_ID.toString()).contentType("application/json")
            .content("{\"name\":\"Nhà cung cấp A\",\"status\":\"ACTIVE\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void purchaseApprovalRequiresObjectScopeAndApprovalPermission() throws Exception {
        when(operationsAccess.canAccessPurchaseRequest(any(),eq(COMPANY_ID),eq(REQUEST_ID),eq("procurement.approve"))).thenReturn(false);
        mvc.perform(post("/api/v1/operations/purchase-requests/{id}/decision",REQUEST_ID).param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
        when(operationsAccess.canAccessPurchaseRequest(any(),eq(COMPANY_ID),eq(REQUEST_ID),eq("procurement.approve"))).thenReturn(true);
        mvc.perform(post("/api/v1/operations/purchase-requests/{id}/decision",REQUEST_ID).param("companyId",COMPANY_ID.toString())
            .contentType("application/json").content("{\"status\":\"APPROVED\"}").with(oidcLogin()).with(csrf())).andExpect(status().isOk());
    }

    @Test void inventoryMutationRequiresWritePermission() throws Exception {
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("inventory.read"))).thenReturn(true);
        when(companyAccess.canAccess(any(),eq(COMPANY_ID),eq("inventory.write"))).thenReturn(false);
        when(operationsService.inventoryItems(COMPANY_ID)).thenReturn(List.of());
        mvc.perform(get("/api/v1/operations/items").param("companyId",COMPANY_ID.toString()).with(oidcLogin())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/operations/items").param("companyId",COMPANY_ID.toString()).contentType("application/json")
            .content("{\"sku\":\"SKU-1\",\"name\":\"Vật tư\",\"itemType\":\"MATERIAL\",\"unit\":\"cái\",\"minStock\":0,\"standardCost\":0,\"currency\":\"VND\"}")
            .with(oidcLogin()).with(csrf())).andExpect(status().isForbidden());
    }
}
