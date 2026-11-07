package com.company.erp.platform.security;

import com.company.erp.audit.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitFilter rateLimitFilter,
            AuditService auditService,
            ClientRegistrationRepository registrations,
            @Value("${app.frontend-origin}") String frontendOrigin) throws Exception {
        var csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(cookie -> cookie.sameSite("Strict").secure(frontendOrigin.startsWith("https://")));

        var oidcLogout = new OidcClientInitiatedLogoutSuccessHandler(registrations);
        oidcLogout.setPostLogoutRedirectUri(frontendOrigin + "/login");

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource(frontendOrigin)))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/api/v1/auth/csrf", "/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                .requestMatchers("/actuator/prometheus").hasAuthority("platform.metrics.read")
                .anyRequest().authenticated())
            .oauth2Login(login -> login
                .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(authoritiesMapper()))
                .successHandler((request, response, authentication) -> {
                    auditService.authenticationEvent(authentication, "AUTH_LOGIN_SUCCEEDED", request);
                    response.sendRedirect(frontendOrigin + "/");
                }))
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .addLogoutHandler((request, response, authentication) -> auditService.authenticationEvent(authentication, "AUTH_LOGOUT", request))
                .logoutSuccessHandler(oidcLogout)
                .invalidateHttpSession(true)
                .deleteCookies("SESSION", "XSRF-TOKEN"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Authentication required\",\"status\":401}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/problem+json");
                    response.getWriter().write("{\"title\":\"Access denied\",\"status\":403}");
                }))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .frameOptions(frame -> frame.deny()))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    GrantedAuthoritiesMapper authoritiesMapper() {
        return source -> {
            Set<GrantedAuthority> mapped = new LinkedHashSet<>(source);
            for (GrantedAuthority authority : source) {
                if (!(authority instanceof OidcUserAuthority oidc)) continue;
                addRoles(mapped, oidc.getIdToken().getClaims().get("realm_access"));
                Object resources = oidc.getIdToken().getClaims().get("resource_access");
                if (resources instanceof Map<?, ?> resourceMap) resourceMap.values().forEach(value -> addRoles(mapped, value));
            }
            return mapped;
        };
    }

    private static void addRoles(Set<GrantedAuthority> target, Object claim) {
        if (!(claim instanceof Map<?, ?> map)) return;
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> values)) return;
        values.stream().map(String::valueOf).forEach(role -> {
            target.add(new SimpleGrantedAuthority(role));
            target.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase().replace('-', '_').replace('.', '_')));
        });
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.frontend-origin}") String frontendOrigin) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(frontendOrigin));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Content-Type", "X-XSRF-TOKEN", "X-Company-Id", "X-Request-Id"));
        configuration.setExposedHeaders(java.util.List.of("X-Request-Id", "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
