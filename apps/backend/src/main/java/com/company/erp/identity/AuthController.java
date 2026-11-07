package com.company.erp.identity;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identities;

    public AuthController(IdentityService identities) { this.identities = identities; }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @GetMapping("/me")
    MeResponse me(Authentication authentication, HttpSession session) {
        OidcUser user = (OidcUser) authentication.getPrincipal();
        identities.provision(user);
        Set<String> permissions = new TreeSet<>();
        authentication.getAuthorities().forEach(authority -> permissions.add(authority.getAuthority()));
        Instant expiresAt = Instant.ofEpochMilli(session.getLastAccessedTime()).plusSeconds(session.getMaxInactiveInterval());
        return new MeResponse(user.getSubject(), user.getEmail(), user.getFullName(), permissions, identities.companies(authentication), expiresAt);
    }

    public record MeResponse(String id, String email, String name, Set<String> permissions,
            List<IdentityService.CompanyView> companies, Instant sessionExpiresAt) {}
}
