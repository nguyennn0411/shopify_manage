package com.company.erp.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private final StringRedisTemplate redis;
    private final int readLimit;
    private final int writeLimit;

    public RateLimitFilter(StringRedisTemplate redis,
            @Value("${app.rate-limit.read-per-five-minutes:300}") int readLimit,
            @Value("${app.rate-limit.write-per-five-minutes:120}") int writeLimit) {
        this.redis = redis;
        this.readLimit = readLimit;
        this.writeLimit = writeLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean write = !SetHolder.SAFE_METHODS.contains(request.getMethod());
        int limit = write ? writeLimit : readLimit;
        long window = System.currentTimeMillis() / Duration.ofMinutes(5).toMillis();
        String key = "company-erp:rate:" + request.getRemoteAddr() + ":" + (write ? "w:" : "r:") + window;
        try {
            Long used = redis.opsForValue().increment(key);
            if (used != null && used == 1) redis.expire(key, Duration.ofMinutes(6));
            long remaining = Math.max(0, limit - (used == null ? 0 : used));
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
            if (used != null && used > limit) {
                response.setStatus(429);
                response.setContentType("application/problem+json");
                response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429}");
                return;
            }
        } catch (RuntimeException unavailable) {
            log.error("Rate limiter unavailable", unavailable);
        }
        chain.doFilter(request, response);
    }

    private static final class SetHolder {
        private static final java.util.Set<String> SAFE_METHODS = java.util.Set.of("GET", "HEAD", "OPTIONS");
    }
}
