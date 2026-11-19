package com.company.erp.pm;

import com.company.erp.company.CompanyAccess;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("pmAccess")
public class PmAccess {
    private final CompanyAccess companyAccess;
    private final JdbcClient jdbc;

    public PmAccess(CompanyAccess companyAccess, JdbcClient jdbc) {
        this.companyAccess = companyAccess;
        this.jdbc = jdbc;
    }

    public boolean canViewTimesheets(Authentication authentication, UUID companyId, UUID employeeId) {
        return canViewTimesheets(authentication, companyId, employeeId, null, null);
    }

    public boolean canAccess(Authentication authentication, UUID companyId, String permission) {
        return companyAccess.canAccess(authentication, companyId, permission);
    }

    public boolean canAccessProject(Authentication authentication, UUID companyId, UUID projectId, String permission) {
        if (!companyAccess.canAccess(authentication, companyId, permission) || projectId == null) return false;
        if (hasGlobalAccess(authentication, companyId, permission)) return true;
        return jdbc.sql("""
            select count(*) from pm_projects p
            left join departments d on d.id=p.department_id and d.company_id=p.company_id
            left join employees owner on owner.id=p.owner_employee_id and owner.company_id=p.company_id
            where p.company_id=:companyId and p.id=:projectId and (
              owner.keycloak_subject=:subject
              or exists (select 1 from pm_project_members m join employees e on e.id=m.employee_id
                where m.company_id=p.company_id and m.project_id=p.id and e.keycloak_subject=:subject)
              or exists (select 1 from company_membership_scopes s
                join company_memberships cm on cm.company_id=s.company_id and cm.keycloak_subject=s.keycloak_subject
                where s.company_id=p.company_id and s.keycloak_subject=:subject and cm.status='ACTIVE' and (
                  (s.scope_type='PROJECT' and s.scope_id=p.id)
                  or (s.scope_type='DEPARTMENT' and s.scope_id=p.department_id)
                  or (s.scope_type='BRANCH' and s.scope_id=d.branch_id)
                ))
            )
            """).param("companyId", companyId.toString()).param("projectId", projectId.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    public boolean canAccessSprint(Authentication authentication, UUID companyId, UUID sprintId, String permission) {
        UUID projectId = jdbc.sql("select project_id from pm_sprints where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", sprintId.toString())
            .query(String.class).optional().map(UUID::fromString).orElse(null);
        return canAccessProject(authentication, companyId, projectId, permission);
    }

    public boolean canAccessTask(Authentication authentication, UUID companyId, UUID taskId, String permission) {
        if (!companyAccess.canAccess(authentication, companyId, permission) || taskId == null) return false;
        TaskRef task = jdbc.sql("""
            select project_id,department_id,assignee_employee_id,supervisor_employee_id
            from pm_tasks where company_id=:companyId and id=:id
            """).param("companyId", companyId.toString()).param("id", taskId.toString())
            .query((rs, row) -> new TaskRef(uuid(rs.getString("project_id")), uuid(rs.getString("department_id")),
                uuid(rs.getString("assignee_employee_id")), uuid(rs.getString("supervisor_employee_id"))))
            .optional().orElse(null);
        if (task == null) return false;
        if (task.projectId() != null) return canAccessProject(authentication, companyId, task.projectId(), permission);
        if (hasGlobalAccess(authentication, companyId, permission)) return true;
        if (task.departmentId() != null && companyAccess.hasScope(authentication, companyId, "DEPARTMENT", task.departmentId())) return true;
        return isEmployeeSubject(companyId, task.assigneeId(), authentication.getName())
            || isEmployeeSubject(companyId, task.supervisorId(), authentication.getName());
    }

    public boolean canAccessIssue(Authentication authentication, UUID companyId, UUID issueId, String permission) {
        if (!companyAccess.canAccess(authentication, companyId, permission) || issueId == null) return false;
        IssueRef issue = jdbc.sql("""
            select project_id,task_id,reporter_subject,assignee_employee_id
            from pm_issues where company_id=:companyId and id=:id
            """).param("companyId", companyId.toString()).param("id", issueId.toString())
            .query((rs, row) -> new IssueRef(uuid(rs.getString("project_id")), uuid(rs.getString("task_id")),
                rs.getString("reporter_subject"), uuid(rs.getString("assignee_employee_id"))))
            .optional().orElse(null);
        if (issue == null) return false;
        if (issue.projectId() != null) return canAccessProject(authentication, companyId, issue.projectId(), permission);
        if (issue.taskId() != null) return canAccessTask(authentication, companyId, issue.taskId(), permission);
        return hasGlobalAccess(authentication, companyId, permission)
            || authentication.getName().equals(issue.reporterSubject())
            || isEmployeeSubject(companyId, issue.assigneeId(), authentication.getName());
    }

    public boolean canCreateInProject(Authentication authentication, UUID companyId, UUID projectId, String permission) {
        if (projectId == null) return companyAccess.canAccess(authentication, companyId, permission);
        return canAccessProject(authentication, companyId, projectId, permission);
    }

    public boolean canViewTimesheets(Authentication authentication, UUID companyId, UUID employeeId,
            UUID projectId, UUID taskId) {
        if (employeeId != null && companyAccess.canAccess(authentication, companyId, "pm.timesheet.read.own")
                && isEmployeeSubject(companyId, employeeId, authentication.getName())) return true;
        if (!companyAccess.canAccess(authentication, companyId, "pm.timesheet.read")) return false;
        if (projectId != null) return canAccessProject(authentication, companyId, projectId, "pm.timesheet.read");
        if (taskId != null) return canAccessTask(authentication, companyId, taskId, "pm.timesheet.read");
        if (employeeId != null) return hasEmployeeScope(authentication, companyId, employeeId, "pm.timesheet.read");
        return hasGlobalAccess(authentication, companyId, "pm.timesheet.read");
    }

    public boolean canCreateTimesheet(Authentication authentication, UUID companyId, UUID employeeId,
            UUID projectId, UUID taskId) {
        if (!companyAccess.canAccess(authentication, companyId, "pm.timesheet.write")) return false;
        if (projectId != null && !canAccessProject(authentication, companyId, projectId, "pm.timesheet.write")) return false;
        if (taskId != null && !canAccessTask(authentication, companyId, taskId, "pm.timesheet.write")) return false;
        return employeeId == null || isEmployeeSubject(companyId, employeeId, authentication.getName())
            || hasEmployeeScope(authentication, companyId, employeeId, "pm.timesheet.write");
    }

    public boolean canReviewTimesheet(Authentication authentication, UUID companyId, UUID entryId) {
        if (!companyAccess.canAccess(authentication, companyId, "pm.timesheet.approve")) return false;
        TimesheetRef ref = jdbc.sql("select employee_id,project_id,task_id from pm_timesheets where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", entryId.toString())
            .query((rs, row) -> new TimesheetRef(uuid(rs.getString("employee_id")), uuid(rs.getString("project_id")),
                uuid(rs.getString("task_id")))).optional().orElse(null);
        if (ref == null) return false;
        if (ref.projectId() != null) return canAccessProject(authentication, companyId, ref.projectId(), "pm.timesheet.approve");
        if (ref.taskId() != null) return canAccessTask(authentication, companyId, ref.taskId(), "pm.timesheet.approve");
        return hasEmployeeScope(authentication, companyId, ref.employeeId(), "pm.timesheet.approve");
    }

    public boolean canManageCapacity(Authentication authentication, UUID companyId, UUID employeeId) {
        return companyAccess.canAccess(authentication, companyId, "pm.workload.write")
            && hasEmployeeScope(authentication, companyId, employeeId, "pm.workload.write");
    }

    private boolean hasEmployeeScope(Authentication authentication, UUID companyId, UUID employeeId, String permission) {
        if (employeeId == null || !companyAccess.canAccess(authentication, companyId, permission)) return false;
        if (hasGlobalAccess(authentication, companyId, permission)
                || isEmployeeSubject(companyId, employeeId, authentication.getName())) return true;
        return jdbc.sql("""
            select count(*) from employees e
            left join departments d on d.id=e.department_id and d.company_id=e.company_id
            where e.company_id=:companyId and e.id=:employeeId and exists (
              select 1 from company_membership_scopes s
              join company_memberships cm on cm.company_id=s.company_id and cm.keycloak_subject=s.keycloak_subject
              where s.company_id=e.company_id and s.keycloak_subject=:subject and cm.status='ACTIVE' and (
                (s.scope_type='SELF' and s.scope_id=e.id)
                or (s.scope_type='TEAM' and (s.scope_id=e.id or s.scope_id=e.manager_employee_id))
                or (s.scope_type='DEPARTMENT' and s.scope_id=e.department_id)
                or (s.scope_type='BRANCH' and (s.scope_id=e.branch_id or s.scope_id=d.branch_id))
              ))
            """).param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .param("subject", authentication.getName()).query(Integer.class).single() > 0;
    }

    private boolean hasGlobalAccess(Authentication authentication, UUID companyId, String permission) {
        return hasDirectAuthority(authentication, permission)
            || companyAccess.hasScope(authentication, companyId, "COMPANY", companyId);
    }

    private boolean hasDirectAuthority(Authentication authentication, String permission) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority ->
            authority.getAuthority().equals(permission) || authority.getAuthority().equals("super-admin")
                || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private boolean isEmployeeSubject(UUID companyId, UUID employeeId, String subject) {
        if (employeeId == null) return false;
        return jdbc.sql("select count(*) from employees where company_id=:companyId and id=:employeeId and keycloak_subject=:subject")
            .param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .param("subject", subject).query(Integer.class).single() > 0;
    }

    private static UUID uuid(String value) { return value == null ? null : UUID.fromString(value.trim()); }

    private record TaskRef(UUID projectId, UUID departmentId, UUID assigneeId, UUID supervisorId) {}
    private record IssueRef(UUID projectId, UUID taskId, String reporterSubject, UUID assigneeId) {}
    private record TimesheetRef(UUID employeeId, UUID projectId, UUID taskId) {}
}
