package com.company.erp.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeClientRequestIdAndClearsMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(RequestIdFilter.HEADER, "client-request-123");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-request-123");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeClientRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.addHeader(RequestIdFilter.HEADER, "bad id\r\nInjected: true");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(RequestIdFilter.HEADER))
            .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
}
