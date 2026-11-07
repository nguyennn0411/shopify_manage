package com.company.erp.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    private final JdbcClient jdbc;

    public IdentityService(JdbcClient jdbc) { this.jdbc = jdbc; }

    public void provision(OidcUser user) {
        jdbc.sql("insert into erp_users(id,keycloak_subject,email,display_name,status,created_at,updated_at) values(:id,:subject,:email,:name,'ACTIVE',current_timestamp,current_timestamp) on conflict (keycloak_subject) do update set email=excluded.email, display_name=excluded.display_name, updated_at=current_timestamp")
            .param("id", UUID.randomUUID().toString()).param("subject", user.getSubject())
            .param("email", user.getEmail() == null ? user.getPreferredUsername() : user.getEmail())
            .param("name", user.getFullName() == null ? user.getPreferredUsername() : user.getFullName()).update();
    }

    public List<CompanyView> companies(Authentication authentication) {
        boolean superAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("super-admin") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        String sql = superAdmin
            ? "select id,name,code,base_currency from companies where status='ACTIVE' order by name"
            : "select c.id,c.name,c.code,c.base_currency from companies c join company_memberships m on m.company_id=c.id where m.keycloak_subject=:subject and c.status='ACTIVE' order by c.name";
        var query = jdbc.sql(sql);
        if (!superAdmin) query.param("subject", authentication.getName());
        return query.query((rs, row) -> new CompanyView(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("code"), rs.getString("base_currency"))).list();
    }

    public record CompanyView(UUID id, String name, String code, String baseCurrency) {}
}
