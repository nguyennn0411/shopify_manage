package com.company.erp.organization;

import static com.company.erp.organization.OrganizationModels.*;

import com.company.erp.audit.AuditService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationService {
    private final JdbcClient jdbc;
    private final AuditService audit;

    public OrganizationService(JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public CompanyProfile profile(UUID companyId) {
        return jdbc.sql("""
            select id,name,code,legal_name,brand_name,tax_code,registration_number,email,phone,website,address,
                   logo_object_key,bank_name,bank_account_name,bank_account_number,base_currency,timezone,status
            from companies where id=:companyId
            """).param("companyId", companyId.toString()).query((rs, row) -> companyProfile(rs)).optional()
            .orElseThrow(() -> notFound("Company"));
    }

    @Transactional
    public CompanyProfile updateProfile(UUID companyId, CompanyProfileUpdate request, String actor) {
        int changed = jdbc.sql("""
            update companies set name=coalesce(:name,name),legal_name=coalesce(:legalName,legal_name),
              brand_name=coalesce(:brandName,brand_name),tax_code=coalesce(:taxCode,tax_code),
              registration_number=coalesce(:registrationNumber,registration_number),email=coalesce(:email,email),
              phone=coalesce(:phone,phone),website=coalesce(:website,website),address=coalesce(:address,address),
              logo_object_key=coalesce(:logoObjectKey,logo_object_key),bank_name=coalesce(:bankName,bank_name),
              bank_account_name=coalesce(:bankAccountName,bank_account_name),
              bank_account_number=coalesce(:bankAccountNumber,bank_account_number),
              base_currency=coalesce(:baseCurrency,base_currency),timezone=coalesce(:timezone,timezone),
              updated_at=current_timestamp where id=:companyId
            """).param("companyId", companyId.toString()).param("name", clean(request.name()))
            .param("legalName", clean(request.legalName())).param("brandName", clean(request.brandName()))
            .param("taxCode", clean(request.taxCode())).param("registrationNumber", clean(request.registrationNumber()))
            .param("email", clean(request.email())).param("phone", clean(request.phone()))
            .param("website", clean(request.website())).param("address", clean(request.address()))
            .param("logoObjectKey", clean(request.logoObjectKey())).param("bankName", clean(request.bankName()))
            .param("bankAccountName", clean(request.bankAccountName()))
            .param("bankAccountNumber", clean(request.bankAccountNumber()))
            .param("baseCurrency", clean(request.baseCurrency())).param("timezone", clean(request.timezone())).update();
        if (changed == 0) throw notFound("Company");
        audit.write(companyId, actor, "COMPANY_PROFILE_UPDATED", "COMPANY", companyId.toString(), "{}");
        return profile(companyId);
    }

    public List<BranchView> branches(UUID companyId) {
        return jdbc.sql("""
            select b.*,e.full_name manager_name from company_branches b
            left join employees e on e.id=b.manager_employee_id and e.company_id=b.company_id
            where b.company_id=:companyId order by b.name
            """).param("companyId", companyId.toString()).query((rs, row) -> branch(rs)).list();
    }

    @Transactional
    public BranchView createBranch(UUID companyId, BranchRequest request, String actor) {
        assertOwned(companyId, "employees", request.managerEmployeeId(), "Manager");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into company_branches(id,company_id,code,name,address,timezone,phone,email,manager_employee_id,status)
            values(:id,:companyId,:code,:name,:address,:timezone,:phone,:email,:manager,:status)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim()).param("name", request.name().trim())
            .param("address", clean(request.address())).param("timezone", value(request.timezone(), "Asia/Ho_Chi_Minh"))
            .param("phone", clean(request.phone())).param("email", clean(request.email()))
            .param("manager", string(request.managerEmployeeId())).param("status", value(request.status(), "ACTIVE")).update();
        audit.write(companyId, actor, "BRANCH_CREATED", "BRANCH", id.toString(), "{}");
        return findBranch(companyId, id);
    }

    @Transactional
    public BranchView updateBranch(UUID companyId, UUID branchId, BranchRequest request, String actor) {
        assertOwned(companyId, "employees", request.managerEmployeeId(), "Manager");
        int changed = jdbc.sql("""
            update company_branches set code=:code,name=:name,address=:address,timezone=:timezone,phone=:phone,
              email=:email,manager_employee_id=:manager,status=:status,updated_at=current_timestamp
            where id=:id and company_id=:companyId
            """).param("id", branchId.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim()).param("name", request.name().trim())
            .param("address", clean(request.address())).param("timezone", value(request.timezone(), "Asia/Ho_Chi_Minh"))
            .param("phone", clean(request.phone())).param("email", clean(request.email()))
            .param("manager", string(request.managerEmployeeId())).param("status", value(request.status(), "ACTIVE")).update();
        if (changed == 0) throw notFound("Branch");
        audit.write(companyId, actor, "BRANCH_UPDATED", "BRANCH", branchId.toString(), "{}");
        return findBranch(companyId, branchId);
    }

    public List<DepartmentView> departments(UUID companyId) {
        return jdbc.sql("""
            select d.*,b.name branch_name,p.name parent_name,e.full_name manager_name from departments d
            left join company_branches b on b.id=d.branch_id and b.company_id=d.company_id
            left join departments p on p.id=d.parent_department_id and p.company_id=d.company_id
            left join employees e on e.id=d.manager_employee_id and e.company_id=d.company_id
            where d.company_id=:companyId order by d.name
            """).param("companyId", companyId.toString()).query((rs, row) -> department(rs)).list();
    }

    @Transactional
    public DepartmentView createDepartment(UUID companyId, DepartmentRequest request, String actor) {
        validateDepartmentReferences(companyId, null, request);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into departments(id,company_id,code,name,function_type,branch_id,parent_department_id,
              manager_employee_id,description,status)
            values(:id,:companyId,:code,:name,:functionType,:branch,:parent,:manager,:description,:status)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim()).param("name", request.name().trim())
            .param("functionType", request.functionType().trim()).param("branch", string(request.branchId()))
            .param("parent", string(request.parentDepartmentId())).param("manager", string(request.managerEmployeeId()))
            .param("description", clean(request.description())).param("status", value(request.status(), "ACTIVE")).update();
        audit.write(companyId, actor, "DEPARTMENT_CREATED", "DEPARTMENT", id.toString(), "{}");
        return findDepartment(companyId, id);
    }

    @Transactional
    public DepartmentView updateDepartment(UUID companyId, UUID departmentId, DepartmentRequest request, String actor) {
        validateDepartmentReferences(companyId, departmentId, request);
        int changed = jdbc.sql("""
            update departments set code=:code,name=:name,function_type=:functionType,branch_id=:branch,
              parent_department_id=:parent,manager_employee_id=:manager,description=:description,
              status=:status,updated_at=current_timestamp where id=:id and company_id=:companyId
            """).param("id", departmentId.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim()).param("name", request.name().trim())
            .param("functionType", request.functionType().trim()).param("branch", string(request.branchId()))
            .param("parent", string(request.parentDepartmentId())).param("manager", string(request.managerEmployeeId()))
            .param("description", clean(request.description())).param("status", value(request.status(), "ACTIVE")).update();
        if (changed == 0) throw notFound("Department");
        audit.write(companyId, actor, "DEPARTMENT_UPDATED", "DEPARTMENT", departmentId.toString(), "{}");
        return findDepartment(companyId, departmentId);
    }

    public List<PositionView> positions(UUID companyId) {
        return jdbc.sql("""
            select p.*,d.name department_name,parent.title reports_to_title from job_positions p
            left join departments d on d.id=p.department_id and d.company_id=p.company_id
            left join job_positions parent on parent.id=p.reports_to_position_id and parent.company_id=p.company_id
            where p.company_id=:companyId order by p.title
            """).param("companyId", companyId.toString()).query((rs, row) -> position(rs)).list();
    }

    @Transactional
    public PositionView createPosition(UUID companyId, PositionRequest request, String actor) {
        validatePositionReferences(companyId, null, request);
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into job_positions(id,company_id,department_id,code,title,level_name,reports_to_position_id,description,status)
            values(:id,:companyId,:department,:code,:title,:levelName,:parent,:description,:status)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("department", string(request.departmentId())).param("code", request.code().trim())
            .param("title", request.title().trim()).param("levelName", clean(request.levelName()))
            .param("parent", string(request.reportsToPositionId())).param("description", clean(request.description()))
            .param("status", value(request.status(), "ACTIVE")).update();
        audit.write(companyId, actor, "POSITION_CREATED", "POSITION", id.toString(), "{}");
        return findPosition(companyId, id);
    }

    @Transactional
    public PositionView updatePosition(UUID companyId, UUID positionId, PositionRequest request, String actor) {
        validatePositionReferences(companyId, positionId, request);
        int changed = jdbc.sql("""
            update job_positions set department_id=:department,code=:code,title=:title,level_name=:levelName,
              reports_to_position_id=:parent,description=:description,status=:status,updated_at=current_timestamp
            where id=:id and company_id=:companyId
            """).param("id", positionId.toString()).param("companyId", companyId.toString())
            .param("department", string(request.departmentId())).param("code", request.code().trim())
            .param("title", request.title().trim()).param("levelName", clean(request.levelName()))
            .param("parent", string(request.reportsToPositionId())).param("description", clean(request.description()))
            .param("status", value(request.status(), "ACTIVE")).update();
        if (changed == 0) throw notFound("Position");
        audit.write(companyId, actor, "POSITION_UPDATED", "POSITION", positionId.toString(), "{}");
        return findPosition(companyId, positionId);
    }

    public OrgChart orgChart(UUID companyId) {
        var employees = jdbc.sql("""
            select id,employee_code,full_name,department_id,manager_employee_id,position_id,job_title,employment_status
            from employees where company_id=:companyId and employment_status<>'ARCHIVED' order by full_name
            """).param("companyId", companyId.toString()).query((rs, row) -> new OrgEmployee(uuid(rs, "id"),
                rs.getString("employee_code"), rs.getString("full_name"), nullableUuid(rs, "department_id"),
                nullableUuid(rs, "manager_employee_id"), nullableUuid(rs, "position_id"), rs.getString("job_title"),
                rs.getString("employment_status"))).list();
        return new OrgChart(branches(companyId), departments(companyId), positions(companyId), employees);
    }

    public List<PermissionView> permissions() {
        return jdbc.sql("select code,module,description,sensitive from permission_catalog order by module,code")
            .query((rs, row) -> new PermissionView(rs.getString("code"), rs.getString("module"),
                rs.getString("description"), rs.getBoolean("sensitive"))).list();
    }

    public List<RoleView> roles(UUID companyId) {
        return jdbc.sql("select * from company_roles where company_id=:companyId order by system_role desc,name")
            .param("companyId", companyId.toString()).query((rs, row) -> role(rs)).list();
    }

    @Transactional
    public RoleView createRole(UUID companyId, RoleRequest request, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into company_roles(id,company_id,code,name,description,system_role,active)
            values(:id,:companyId,:code,:name,:description,false,true)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("code", request.code().trim().toUpperCase()).param("name", request.name().trim())
            .param("description", clean(request.description())).update();
        audit.write(companyId, actor, "ROLE_CREATED", "COMPANY_ROLE", id.toString(), "{}");
        return findRole(companyId, id);
    }

    @Transactional
    public RoleView replaceRolePermissions(UUID companyId, UUID roleId, RolePermissionsRequest request, String actor) {
        requireOwned(companyId, "company_roles", roleId, "Role");
        long valid = jdbc.sql("select count(*) from permission_catalog where code in (:codes)")
            .param("codes", request.permissions().stream().distinct().toList()).query(Long.class).single();
        long requested = request.permissions().stream().distinct().count();
        if (valid != requested) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission");
        jdbc.sql("delete from company_role_permissions where role_id=:roleId").param("roleId", roleId.toString()).update();
        request.permissions().stream().distinct().forEach(permission -> jdbc.sql(
            "insert into company_role_permissions(role_id,permission_code) values(:roleId,:permission)")
            .param("roleId", roleId.toString()).param("permission", permission).update());
        audit.write(companyId, actor, "ROLE_PERMISSIONS_REPLACED", "COMPANY_ROLE", roleId.toString(), "{}");
        return findRole(companyId, roleId);
    }

    public List<MemberView> members(UUID companyId) {
        return jdbc.sql("""
            select m.keycloak_subject,u.email,u.display_name,m.status from company_memberships m
            join erp_users u on u.keycloak_subject=m.keycloak_subject
            where m.company_id=:companyId order by u.display_name
            """).param("companyId", companyId.toString()).query((rs, row) -> member(companyId,
                rs.getString("keycloak_subject"), rs.getString("email"), rs.getString("display_name"),
                rs.getString("status"))).list();
    }

    @Transactional
    public MemberView addMember(UUID companyId, MemberRequest request, String actor) {
        jdbc.sql("""
            insert into erp_users(id,keycloak_subject,email,display_name,status)
            values(:id,:subject,:email,:displayName,'ACTIVE')
            on conflict(keycloak_subject) do update set email=excluded.email,display_name=excluded.display_name,
              updated_at=current_timestamp
            """).param("id", UUID.randomUUID().toString()).param("subject", request.keycloakSubject().trim())
            .param("email", request.email().trim()).param("displayName", request.displayName().trim()).update();
        String status = value(request.status(), "INVITED");
        jdbc.sql("""
            insert into company_memberships(company_id,keycloak_subject,status,invited_at,activated_at)
            values(:companyId,:subject,:status,current_timestamp,
              case when :status='ACTIVE' then current_timestamp else null end)
            on conflict(company_id,keycloak_subject) do update set status=excluded.status,
              invited_at=coalesce(company_memberships.invited_at,current_timestamp),
              activated_at=case when excluded.status='ACTIVE' then current_timestamp else company_memberships.activated_at end,
              disabled_at=null
            """).param("companyId", companyId.toString()).param("subject", request.keycloakSubject().trim())
            .param("status", status).update();
        audit.write(companyId, actor, "MEMBER_ADDED", "COMPANY_MEMBER", request.keycloakSubject(), "{}");
        return findMember(companyId, request.keycloakSubject());
    }

    @Transactional
    public MemberView updateMemberStatus(UUID companyId, String subject, MemberStatusRequest request, String actor) {
        if (actor.equals(subject) && request.status().equals("DISABLED"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot disable your own membership");
        int changed = jdbc.sql("""
            update company_memberships set status=:status,
              activated_at=case when :status='ACTIVE' then current_timestamp else activated_at end,
              disabled_at=case when :status='DISABLED' then current_timestamp else null end
            where company_id=:companyId and keycloak_subject=:subject
            """).param("status", request.status()).param("companyId", companyId.toString())
            .param("subject", subject).update();
        if (changed == 0) throw notFound("Member");
        audit.write(companyId, actor, "MEMBER_STATUS_UPDATED", "COMPANY_MEMBER", subject, "{}");
        return findMember(companyId, subject);
    }

    @Transactional
    public MemberView replaceMemberAccess(UUID companyId, String subject, MemberAccessRequest request, String actor) {
        requireMember(companyId, subject);
        List<UUID> roleIds = request.roleIds() == null ? List.of() : request.roleIds().stream().distinct().toList();
        for (UUID roleId : roleIds) requireOwned(companyId, "company_roles", roleId, "Role");
        List<ScopeGrant> scopes = request.scopes() == null ? List.of() : request.scopes();
        for (ScopeGrant scope : scopes) validateScope(companyId, scope);
        jdbc.sql("delete from company_membership_roles where company_id=:companyId and keycloak_subject=:subject")
            .param("companyId", companyId.toString()).param("subject", subject).update();
        jdbc.sql("delete from company_membership_scopes where company_id=:companyId and keycloak_subject=:subject")
            .param("companyId", companyId.toString()).param("subject", subject).update();
        roleIds.forEach(roleId -> jdbc.sql("""
            insert into company_membership_roles(company_id,keycloak_subject,role_id)
            values(:companyId,:subject,:roleId)
            """).param("companyId", companyId.toString()).param("subject", subject)
            .param("roleId", roleId.toString()).update());
        scopes.stream().distinct().forEach(scope -> jdbc.sql("""
            insert into company_membership_scopes(company_id,keycloak_subject,scope_type,scope_id)
            values(:companyId,:subject,:scopeType,:scopeId)
            """).param("companyId", companyId.toString()).param("subject", subject)
            .param("scopeType", scope.scopeType()).param("scopeId", scope.scopeId().toString()).update());
        audit.write(companyId, actor, "MEMBER_ACCESS_REPLACED", "COMPANY_MEMBER", subject, "{}");
        return findMember(companyId, subject);
    }

    private BranchView findBranch(UUID companyId, UUID id) {
        return branches(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("Branch"));
    }

    private DepartmentView findDepartment(UUID companyId, UUID id) {
        return departments(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("Department"));
    }

    private PositionView findPosition(UUID companyId, UUID id) {
        return positions(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("Position"));
    }

    private RoleView findRole(UUID companyId, UUID id) {
        return roles(companyId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(() -> notFound("Role"));
    }

    private MemberView findMember(UUID companyId, String subject) {
        return members(companyId).stream().filter(item -> item.keycloakSubject().equals(subject)).findFirst()
            .orElseThrow(() -> notFound("Member"));
    }

    private RoleView role(ResultSet rs) throws SQLException {
        UUID roleId = uuid(rs, "id");
        List<String> permissions = jdbc.sql("""
            select permission_code from company_role_permissions where role_id=:roleId order by permission_code
            """).param("roleId", roleId.toString()).query(String.class).list();
        return new RoleView(roleId, rs.getString("code"), rs.getString("name"), rs.getString("description"),
            rs.getBoolean("system_role"), rs.getBoolean("active"), permissions);
    }

    private MemberView member(UUID companyId, String subject, String email, String displayName, String status) {
        var roleRows = jdbc.sql("""
            select r.id,r.name from company_membership_roles mr join company_roles r on r.id=mr.role_id
            where mr.company_id=:companyId and mr.keycloak_subject=:subject order by r.name
            """).param("companyId", companyId.toString()).param("subject", subject)
            .query((rs, row) -> new Object[] { uuid(rs, "id"), rs.getString("name") }).list();
        List<UUID> roleIds = roleRows.stream().map(row -> (UUID) row[0]).toList();
        List<String> roleNames = roleRows.stream().map(row -> (String) row[1]).toList();
        List<ScopeView> scopes = jdbc.sql("""
            select scope_type,scope_id from company_membership_scopes
            where company_id=:companyId and keycloak_subject=:subject order by scope_type,scope_id
            """).param("companyId", companyId.toString()).param("subject", subject)
            .query((rs, row) -> new ScopeView(rs.getString("scope_type"), uuid(rs, "scope_id"))).list();
        return new MemberView(subject, email, displayName, status, roleIds, roleNames, scopes);
    }

    private void validateDepartmentReferences(UUID companyId, UUID departmentId, DepartmentRequest request) {
        assertOwned(companyId, "company_branches", request.branchId(), "Branch");
        assertOwned(companyId, "departments", request.parentDepartmentId(), "Parent department");
        assertOwned(companyId, "employees", request.managerEmployeeId(), "Manager");
        if (departmentId != null && departmentId.equals(request.parentDepartmentId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Department cannot be its own parent");
    }

    private void validatePositionReferences(UUID companyId, UUID positionId, PositionRequest request) {
        assertOwned(companyId, "departments", request.departmentId(), "Department");
        assertOwned(companyId, "job_positions", request.reportsToPositionId(), "Reporting position");
        if (positionId != null && positionId.equals(request.reportsToPositionId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Position cannot report to itself");
    }

    private void validateScope(UUID companyId, ScopeGrant scope) {
        if (scope.scopeId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scope ID is required");
        switch (scope.scopeType()) {
            case "COMPANY" -> {
                if (!companyId.equals(scope.scopeId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid company scope");
            }
            case "DEPARTMENT" -> requireOwned(companyId, "departments", scope.scopeId(), "Department scope");
            case "BRANCH" -> requireOwned(companyId, "company_branches", scope.scopeId(), "Branch scope");
            case "PROJECT" -> requireOwned(companyId, "pm_projects", scope.scopeId(), "Project scope");
            case "SELF", "TEAM" -> requireOwned(companyId, "employees", scope.scopeId(), "Employee scope");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scope type");
        }
    }

    private void assertOwned(UUID companyId, String table, UUID id, String label) {
        if (id != null) requireOwned(companyId, table, id, label);
    }

    private void requireOwned(UUID companyId, String table, UUID id, String label) {
        long count = jdbc.sql("select count(*) from " + table + " where id=:id and company_id=:companyId")
            .param("id", id.toString()).param("companyId", companyId.toString()).query(Long.class).single();
        if (count == 0) throw notFound(label);
    }

    private void requireMember(UUID companyId, String subject) {
        long count = jdbc.sql("select count(*) from company_memberships where company_id=:companyId and keycloak_subject=:subject")
            .param("companyId", companyId.toString()).param("subject", subject).query(Long.class).single();
        if (count == 0) throw notFound("Member");
    }

    private static CompanyProfile companyProfile(ResultSet rs) throws SQLException {
        return new CompanyProfile(uuid(rs, "id"), rs.getString("name"), rs.getString("code"), rs.getString("legal_name"),
            rs.getString("brand_name"), rs.getString("tax_code"), rs.getString("registration_number"),
            rs.getString("email"), rs.getString("phone"), rs.getString("website"), rs.getString("address"),
            rs.getString("logo_object_key"), rs.getString("bank_name"), rs.getString("bank_account_name"),
            rs.getString("bank_account_number"), rs.getString("base_currency"), rs.getString("timezone"), rs.getString("status"));
    }

    private static BranchView branch(ResultSet rs) throws SQLException {
        return new BranchView(uuid(rs, "id"), rs.getString("code"), rs.getString("name"), rs.getString("address"),
            rs.getString("timezone"), rs.getString("phone"), rs.getString("email"),
            nullableUuid(rs, "manager_employee_id"), rs.getString("manager_name"), rs.getString("status"));
    }

    private static DepartmentView department(ResultSet rs) throws SQLException {
        return new DepartmentView(uuid(rs, "id"), rs.getString("code"), rs.getString("name"),
            rs.getString("function_type"), nullableUuid(rs, "branch_id"), rs.getString("branch_name"),
            nullableUuid(rs, "parent_department_id"), rs.getString("parent_name"),
            nullableUuid(rs, "manager_employee_id"), rs.getString("manager_name"),
            rs.getString("description"), rs.getString("status"));
    }

    private static PositionView position(ResultSet rs) throws SQLException {
        return new PositionView(uuid(rs, "id"), rs.getString("code"), rs.getString("title"),
            nullableUuid(rs, "department_id"), rs.getString("department_name"), rs.getString("level_name"),
            nullableUuid(rs, "reports_to_position_id"), rs.getString("reports_to_title"),
            rs.getString("description"), rs.getString("status"));
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return UUID.fromString(rs.getString(column).trim());
    }

    private static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value.trim());
    }

    private static String string(UUID value) { return value == null ? null : value.toString(); }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static ResponseStatusException notFound(String label) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }
}
