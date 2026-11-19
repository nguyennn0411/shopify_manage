package com.company.erp.config;

import com.company.erp.company.CompanyReferenceDataProvisioner;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.dev-seed.enabled", havingValue = "true")
public class DevelopmentDataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevelopmentDataSeeder.class);
    private final JdbcClient jdbc;
    private final CompanyReferenceDataProvisioner referenceData;
    private final String companyCode;
    private final String companyName;
    private final String subject;
    private final String email;
    private final String displayName;

    public DevelopmentDataSeeder(JdbcClient jdbc, CompanyReferenceDataProvisioner referenceData,
            @Value("${app.dev-seed.company-code:DEMO}") String companyCode,
            @Value("${app.dev-seed.company-name:Demo Company}") String companyName,
            @Value("${app.dev-seed.keycloak-subject:dev-user-subject}") String subject,
            @Value("${app.dev-seed.email:developer@example.test}") String email,
            @Value("${app.dev-seed.display-name:Development User}") String displayName) {
        this.jdbc = jdbc;
        this.referenceData = referenceData;
        this.companyCode = companyCode;
        this.companyName = companyName;
        this.subject = subject;
        this.email = email;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String companyId = jdbc.sql("""
            insert into companies(id,name,code,base_currency,timezone,status)
            values(:id,:name,:code,'VND','Asia/Ho_Chi_Minh','ACTIVE')
            on conflict(code) do update set name=excluded.name, updated_at=current_timestamp
            returning id
            """).param("id", UUID.randomUUID().toString()).param("name", companyName)
            .param("code", companyCode).query(String.class).single().trim();
        jdbc.sql("""
            insert into erp_users(id,keycloak_subject,email,display_name,status)
            values(:id,:subject,:email,:displayName,'ACTIVE')
            on conflict(keycloak_subject) do update set email=excluded.email,display_name=excluded.display_name,updated_at=current_timestamp
            """).param("id", UUID.randomUUID().toString()).param("subject", subject)
            .param("email", email).param("displayName", displayName).update();
        jdbc.sql("""
            insert into company_memberships(company_id,keycloak_subject)
            values(:companyId,:subject) on conflict(company_id,keycloak_subject) do nothing
            """).param("companyId", companyId).param("subject", subject).update();
        provisionDepartment(companyId, "EXEC", "Ban điều hành", "EXECUTIVE");
        provisionDepartment(companyId, "HR", "Nhân sự", "HUMAN_RESOURCES");
        provisionDepartment(companyId, "TECH", "Công nghệ", "TECHNOLOGY");
        referenceData.provision(UUID.fromString(companyId));
        jdbc.sql("""
            insert into company_membership_roles(company_id,keycloak_subject,role_id)
            select :companyId,:subject,id from company_roles where company_id=:companyId and code='COMPANY_ADMIN'
            on conflict(company_id,keycloak_subject,role_id) do nothing
            """).param("companyId", companyId).param("subject", subject).update();
        jdbc.sql("""
            insert into company_membership_scopes(company_id,keycloak_subject,scope_type,scope_id)
            values(:companyId,:subject,'COMPANY',:companyId)
            on conflict(company_id,keycloak_subject,scope_type,scope_id) do nothing
            """).param("companyId", companyId).param("subject", subject).update();
        log.info("Development seed ready for company code={}", companyCode);
    }

    private void provisionDepartment(String companyId, String code, String name, String type) {
        jdbc.sql("""
            insert into departments(id,company_id,code,name,function_type)
            values(:id,:companyId,:code,:name,:type)
            on conflict(company_id,code) do update set name=excluded.name,function_type=excluded.function_type
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId)
            .param("code", code).param("name", name).param("type", type).update();
    }
}
