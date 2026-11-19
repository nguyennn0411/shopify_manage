package com.company.erp.pm;

import com.company.erp.audit.AuditService;
import com.company.erp.pm.PmModels.*;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PmService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;
    private final PmAccess access;

    public PmService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit, PmAccess access) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.audit = audit;
        this.access = access;
    }

    // ===== Summary =====

    public PmSummary summary(UUID companyId, Authentication authentication) {
        List<ProjectView> visibleProjects = access.canAccess(authentication, companyId, "pm.project.read")
            ? projects(companyId, authentication) : List.of();
        List<Map<String, Object>> visibleTasks = access.canAccess(authentication, companyId, "pm.task.read")
            ? tasks(companyId, null, null, null, null, null, null, null, authentication) : List.of();
        List<Map<String, Object>> visibleSprints = access.canAccess(authentication, companyId, "pm.sprint.read")
            ? sprints(companyId, null, authentication) : List.of();
        List<Map<String, Object>> visibleIssues = access.canAccess(authentication, companyId, "pm.issue.read")
            ? issues(companyId, null, null, null, null, authentication) : List.of();
        LocalDate today = LocalDate.now();
        return new PmSummary(
            access.canAccess(authentication, companyId, "pm.project.read") ? visibleProjects.stream()
                .filter(item -> List.of("PLANNED", "ACTIVE", "ON_HOLD").contains(item.status())).count() : null,
            access.canAccess(authentication, companyId, "pm.task.read") ? (long) visibleTasks.size() : null,
            access.canAccess(authentication, companyId, "pm.task.read") ? visibleTasks.stream()
                .map(item -> localDateValue(item.get("due_date"))).filter(java.util.Objects::nonNull)
                .filter(date -> !date.isBefore(today) && !date.isAfter(today.plusDays(7))).count() : null,
            access.canAccess(authentication, companyId, "pm.sprint.read") ? visibleSprints.stream()
                .filter(item -> "ACTIVE".equals(String.valueOf(item.get("status")))).count() : null,
            access.canViewTimesheets(authentication, companyId, null, null, null)
                ? count("pm_timesheets", "status='SUBMITTED'", companyId) : null,
            access.canAccess(authentication, companyId, "pm.issue.read") ? visibleIssues.stream()
                .filter(item -> List.of("OPEN", "IN_PROGRESS", "REOPENED").contains(String.valueOf(item.get("status")))).count() : null,
            access.canAccess(authentication, companyId, "pm.issue.read") ? visibleIssues.stream()
                .filter(item -> List.of("OPEN", "IN_PROGRESS", "REOPENED").contains(String.valueOf(item.get("status"))))
                .filter(item -> "CRITICAL".equals(String.valueOf(item.get("severity")))).count() : null);
    }

    // ===== Project Management =====

    public List<ProjectView> projects(UUID companyId) {
        return jdbc.sql("""
            select p.id,p.code,p.name,p.status,p.department_id,d.name department_name,p.budget,p.currency,
                   p.starts_on,p.ends_on,p.progress_percent,p.risk_level,
                   (select count(*) from pm_project_members m where m.project_id=p.id and m.company_id=p.company_id) member_count,
                   (select count(*) from pm_tasks t where t.project_id=p.id and t.company_id=p.company_id) task_count,
                   (select coalesce(sum(ts.hours*m.cost_rate),0) from pm_timesheets ts
                      join pm_tasks t on t.id=ts.task_id
                      join pm_project_members m on m.project_id=t.project_id and m.employee_id=ts.employee_id
                     where ts.company_id=p.company_id and ts.status='APPROVED' and t.project_id=p.id) actual_cost
            from pm_projects p left join departments d on d.id=p.department_id
            where p.company_id=:companyId order by p.code
            """).param("companyId", companyId.toString()).query(this::projectView).list();
    }

    public List<ProjectView> projects(UUID companyId, Authentication authentication) {
        return projects(companyId).stream()
            .filter(project -> access.canAccessProject(authentication, companyId, project.id(), "pm.project.read"))
            .toList();
    }

    public ProjectView project(UUID companyId, UUID projectId) {
        return jdbc.sql("""
            select p.id,p.code,p.name,p.status,p.department_id,d.name department_name,p.budget,p.currency,
                   p.starts_on,p.ends_on,p.progress_percent,p.risk_level,
                   (select count(*) from pm_project_members m where m.project_id=p.id and m.company_id=p.company_id) member_count,
                   (select count(*) from pm_tasks t where t.project_id=p.id and t.company_id=p.company_id) task_count,
                   (select coalesce(sum(ts.hours*m.cost_rate),0) from pm_timesheets ts
                      join pm_tasks t on t.id=ts.task_id
                      join pm_project_members m on m.project_id=t.project_id and m.employee_id=ts.employee_id
                     where ts.company_id=p.company_id and ts.status='APPROVED' and t.project_id=p.id) actual_cost
            from pm_projects p left join departments d on d.id=p.department_id
            where p.company_id=:companyId and p.id=:projectId
            """).param("companyId", companyId.toString()).param("projectId", projectId.toString())
            .query(this::projectView).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public CreatedResource createProject(UUID companyId, ProjectCreateRequest request, String actor) {
        if (request.departmentId() != null) ensureCompanyResource("departments", companyId, request.departmentId(), "Department");
        if (request.ownerEmployeeId() != null) ensureCompanyResource("employees", companyId, request.ownerEmployeeId(), "Owner");
        if (request.startsOn() != null && request.endsOn() != null && request.endsOn().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project end date must be on or after start date");
        UUID id = UUID.randomUUID();
        String code = request.code() == null || request.code().isBlank()
            ? nextCode("pm_projects", "PROJ", companyId) : request.code().trim();
        jdbc.sql("""
            insert into pm_projects(id,company_id,code,name,department_id,owner_employee_id,objective,scope,budget,currency,
              status,starts_on,ends_on,progress_percent,risk_level)
            values(:id,:companyId,:code,:name,:departmentId,:ownerId,:objective,:scope,:budget,:currency,
              :status,:startsOn,:endsOn,0,:riskLevel)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("name", request.name()).param("departmentId", uuid(request.departmentId()))
            .param("ownerId", uuid(request.ownerEmployeeId())).param("objective", blankToNull(request.objective()))
            .param("scope", blankToNull(request.scope())).param("budget", request.budget())
            .param("currency", blankToNull(request.currency())).param("status", request.status())
            .param("startsOn", request.startsOn()).param("endsOn", request.endsOn())
            .param("riskLevel", request.riskLevel() == null ? "LOW" : request.riskLevel()).update();
        audit.write(companyId, actor, "PM_PROJECT_CREATED", "PM_PROJECT", id.toString(), "{\"code\":\"" + code + "\"}");
        return new CreatedResource(id, request.status());
    }

    @Transactional
    public ProjectView updateProject(UUID companyId, UUID projectId, ProjectUpdateRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, projectId, "Project");
        if (request.departmentId() != null) ensureCompanyResource("departments", companyId, request.departmentId(), "Department");
        if (request.ownerEmployeeId() != null) ensureCompanyResource("employees", companyId, request.ownerEmployeeId(), "Owner");
        jdbc.sql("""
            update pm_projects set name=coalesce(:name,name),
              department_id=coalesce(:departmentId,department_id),
              owner_employee_id=coalesce(:ownerId,owner_employee_id),
              objective=coalesce(:objective,objective), scope=coalesce(:scope,scope),
              budget=coalesce(:budget,budget), currency=coalesce(:currency,currency),
              starts_on=coalesce(:startsOn,starts_on), ends_on=coalesce(:endsOn,ends_on),
              risk_level=coalesce(:riskLevel,risk_level)
            where id=:id and company_id=:companyId
            """).param("name", blankToNull(request.name())).param("departmentId", uuid(request.departmentId()))
            .param("ownerId", uuid(request.ownerEmployeeId())).param("objective", blankToNull(request.objective()))
            .param("scope", blankToNull(request.scope())).param("budget", request.budget())
            .param("currency", blankToNull(request.currency())).param("startsOn", request.startsOn())
            .param("endsOn", request.endsOn()).param("riskLevel", blankToNull(request.riskLevel()))
            .param("id", projectId.toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_PROJECT_UPDATED", "PM_PROJECT", projectId.toString(), "{}");
        return project(companyId, projectId);
    }

    @Transactional
    public CreatedResource decideProject(UUID companyId, ProjectDecisionRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        jdbc.sql("update pm_projects set status=:status where id=:id and company_id=:companyId")
            .param("status", request.status()).param("id", request.projectId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_PROJECT_STATUS_CHANGED", "PM_PROJECT", request.projectId().toString(),
            "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(request.projectId(), request.status());
    }

    @Transactional
    public CreatedResource addProjectMember(UUID companyId, ProjectMemberRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        String role = request.role() == null || request.role().isBlank() ? "MEMBER" : request.role();
        jdbc.sql("""
            insert into pm_project_members(project_id,company_id,employee_id,role,cost_rate)
            values(:projectId,:companyId,:employeeId,:role,:costRate)
            on conflict (project_id,employee_id) do update set role=excluded.role, cost_rate=excluded.cost_rate
            """).param("projectId", request.projectId().toString()).param("companyId", companyId.toString())
            .param("employeeId", request.employeeId().toString()).param("role", role)
            .param("costRate", request.costRate()).update();
        String subject = jdbc.sql("select keycloak_subject from employees where company_id=:companyId and id=:employeeId")
            .param("companyId", companyId.toString()).param("employeeId", request.employeeId().toString())
            .query(String.class).optional().orElse(null);
        if (subject != null) {
            jdbc.sql("""
                insert into company_membership_scopes(company_id,keycloak_subject,scope_type,scope_id)
                select :companyId,:subject,'PROJECT',:projectId
                where exists (select 1 from company_memberships where company_id=:companyId
                  and keycloak_subject=:subject and status='ACTIVE')
                on conflict do nothing
                """).param("companyId", companyId.toString()).param("subject", subject)
                .param("projectId", request.projectId().toString()).update();
        }
        audit.write(companyId, actor, "PM_PROJECT_MEMBER_ADDED", "PM_PROJECT", request.projectId().toString(),
            "{\"employeeId\":\"" + request.employeeId() + "\"}");
        return new CreatedResource(request.projectId(), role);
    }

    @Transactional
    public CreatedResource addMilestone(UUID companyId, MilestoneRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_milestones(id,company_id,project_id,title,due_date,status)
            values(:id,:companyId,:projectId,:title,:dueDate,'OPEN')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("projectId", request.projectId().toString()).param("title", request.title())
            .param("dueDate", request.dueDate()).update();
        audit.write(companyId, actor, "PM_MILESTONE_CREATED", "PM_MILESTONE", id.toString(), "{}");
        return new CreatedResource(id, "OPEN");
    }

    @Transactional
    public CreatedResource addRisk(UUID companyId, RiskRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_risks(id,company_id,project_id,title,likelihood,impact,mitigation,status,owner_employee_id)
            values(:id,:companyId,:projectId,:title,:likelihood,:impact,:mitigation,'OPEN',:ownerId)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("projectId", request.projectId().toString()).param("title", request.title())
            .param("likelihood", request.likelihood() == null ? "MEDIUM" : request.likelihood())
            .param("impact", request.impact() == null ? "MEDIUM" : request.impact())
            .param("mitigation", blankToNull(request.mitigation())).param("ownerId", uuid(request.ownerEmployeeId())).update();
        audit.write(companyId, actor, "PM_RISK_CREATED", "PM_RISK", id.toString(), "{}");
        return new CreatedResource(id, "OPEN");
    }

    public List<Map<String, Object>> projectMembers(UUID companyId, UUID projectId) {
        return rows("""
            select m.project_id,m.employee_id,e.full_name,e.employee_code,m.role,m.cost_rate,m.created_at
            from pm_project_members m join employees e on e.id=m.employee_id
            where m.company_id=:companyId and m.project_id=:projectId
            order by e.full_name
            """, companyId, Map.of("projectId", projectId.toString()));
    }

    public List<Map<String, Object>> milestones(UUID companyId, UUID projectId) {
        return rows("""
            select id,title,due_date,status from pm_milestones
            where company_id=:companyId and project_id=:projectId order by due_date
            """, companyId, Map.of("projectId", projectId.toString()));
    }

    public List<Map<String, Object>> risks(UUID companyId, UUID projectId) {
        return rows("""
            select r.id,r.title,r.likelihood,r.impact,r.mitigation,r.status,r.owner_employee_id,e.full_name owner_name
            from pm_risks r left join employees e on e.id=r.owner_employee_id
            where r.company_id=:companyId and r.project_id=:projectId order by r.created_at desc
            """, companyId, Map.of("projectId", projectId.toString()));
    }

    // ===== Sprint & Workflow =====

    public List<Map<String, Object>> sprints(UUID companyId, UUID projectId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        if (projectId != null) params.put("projectId", projectId.toString());
        String filter = projectId == null ? "" : " and s.project_id=:projectId";
        return rows("""
            select s.id,s.project_id,p.code project_code,p.name project_name,s.name,s.goal,s.starts_on,s.ends_on,s.status,
                   (select count(*) from pm_tasks t where t.sprint_id=s.id and t.company_id=s.company_id) task_count
            from pm_sprints s join pm_projects p on p.id=s.project_id
            where s.company_id=:companyId""" + filter + " order by s.starts_on desc", companyId, params);
    }

    public List<Map<String, Object>> sprints(UUID companyId, UUID projectId, Authentication authentication) {
        return sprints(companyId, projectId).stream()
            .filter(item -> access.canAccessProject(authentication, companyId, uuidValue(item.get("project_id")), "pm.sprint.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createSprint(UUID companyId, SprintRequest request, String actor) {
        ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        if (request.endsOn().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint end date must be on or after start date");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_sprints(id,company_id,project_id,name,goal,starts_on,ends_on,status)
            values(:id,:companyId,:projectId,:name,:goal,:startsOn,:endsOn,'PLANNED')
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("projectId", request.projectId().toString()).param("name", request.name())
            .param("goal", blankToNull(request.goal())).param("startsOn", request.startsOn())
            .param("endsOn", request.endsOn()).update();
        audit.write(companyId, actor, "PM_SPRINT_CREATED", "PM_SPRINT", id.toString(), "{}");
        return new CreatedResource(id, "PLANNED");
    }

    @Transactional
    public CreatedResource decideSprint(UUID companyId, SprintDecisionRequest request, String actor) {
        ensureCompanyResource("pm_sprints", companyId, request.sprintId(), "Sprint");
        jdbc.sql("update pm_sprints set status=:status where id=:id and company_id=:companyId")
            .param("status", request.status()).param("id", request.sprintId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_SPRINT_STATUS_CHANGED", "PM_SPRINT", request.sprintId().toString(),
            "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(request.sprintId(), request.status());
    }

    public List<Map<String, Object>> workflowStatuses(UUID companyId, String workflowType) {
        String type = workflowType == null || workflowType.isBlank() ? "TASK" : workflowType;
        if ("TASK".equals(type)) ensureDefaultWorkflowStatuses(companyId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        params.put("workflowType", type);
        return rows("""
            select id,workflow_type,code,name,sort_order,color,is_done from pm_workflow_statuses
            where company_id=:companyId and workflow_type=:workflowType and active=true order by sort_order
            """, companyId, params);
    }

    @Transactional
    public CreatedResource createWorkflowStatus(UUID companyId, WorkflowStatusRequest request, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
            values(:id,:companyId,:type,:code,:name,:sortOrder,:color,:isDone,true)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("type", request.workflowType()).param("code", request.code()).param("name", request.name())
            .param("sortOrder", request.sortOrder()).param("color", blankToNull(request.color()))
            .param("isDone", request.isDone()).update();
        audit.write(companyId, actor, "PM_WORKFLOW_STATUS_CREATED", "PM_WORKFLOW_STATUS", id.toString(),
            "{\"code\":\"" + request.code() + "\"}");
        return new CreatedResource(id, request.code());
    }

    // ===== Task Management =====

    public List<Map<String, Object>> tasks(UUID companyId, UUID projectId, UUID sprintId, UUID statusId,
            UUID assigneeId, String priority, LocalDate from, LocalDate to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        StringBuilder filter = new StringBuilder(" where t.company_id=:companyId");
        if (projectId != null) { filter.append(" and t.project_id=:projectId"); params.put("projectId", projectId.toString()); }
        if (sprintId != null) { filter.append(" and t.sprint_id=:sprintId"); params.put("sprintId", sprintId.toString()); }
        if (statusId != null) { filter.append(" and t.status_id=:statusId"); params.put("statusId", statusId.toString()); }
        if (assigneeId != null) { filter.append(" and t.assignee_employee_id=:assigneeId"); params.put("assigneeId", assigneeId.toString()); }
        if (priority != null && !priority.isBlank()) { filter.append(" and t.priority=:priority"); params.put("priority", priority); }
        if (from != null) { filter.append(" and (t.due_date is null or t.due_date>=:from)"); params.put("from", from); }
        if (to != null) { filter.append(" and (t.starts_on is null or t.starts_on<=:to)"); params.put("to", to); }
        return rows("""
            select t.id,t.code,t.title,t.description,t.project_id,p.code project_code,p.name project_name,
                   t.sprint_id,s.name sprint_name,t.department_id,d.name department_name,
                   t.assignee_employee_id,ae.full_name assignee_name,t.supervisor_employee_id,se.full_name supervisor_name,
                   t.parent_task_id,t.status_id,ws.code status_code,ws.name status_name,ws.color status_color,ws.is_done,
                   t.priority,t.progress_percent,t.estimated_hours,t.starts_on,t.due_date,t.watcher_subjects,t.created_at
            from pm_tasks t
            left join pm_projects p on p.id=t.project_id
            left join pm_sprints s on s.id=t.sprint_id
            left join departments d on d.id=t.department_id
            left join employees ae on ae.id=t.assignee_employee_id
            left join employees se on se.id=t.supervisor_employee_id
            join pm_workflow_statuses ws on ws.id=t.status_id""" + filter
            + " order by t.due_date is null, t.due_date, t.code", companyId, params);
    }

    public List<Map<String, Object>> tasks(UUID companyId, UUID projectId, UUID sprintId, UUID statusId,
            UUID assigneeId, String priority, LocalDate from, LocalDate to, Authentication authentication) {
        return tasks(companyId, projectId, sprintId, statusId, assigneeId, priority, from, to).stream()
            .filter(item -> access.canAccessTask(authentication, companyId, uuidValue(item.get("id")), "pm.task.read"))
            .toList();
    }

    public Map<String, Object> task(UUID companyId, UUID taskId) {
        List<Map<String, Object>> result = rows("""
            select t.id,t.code,t.title,t.description,t.project_id,p.code project_code,p.name project_name,
                   t.sprint_id,s.name sprint_name,t.department_id,d.name department_name,
                   t.assignee_employee_id,ae.full_name assignee_name,t.supervisor_employee_id,se.full_name supervisor_name,
                   t.parent_task_id,t.status_id,ws.code status_code,ws.name status_name,ws.color status_color,ws.is_done,
                   t.priority,t.progress_percent,t.estimated_hours,t.starts_on,t.due_date,t.watcher_subjects,t.created_at
            from pm_tasks t
            left join pm_projects p on p.id=t.project_id
            left join pm_sprints s on s.id=t.sprint_id
            left join departments d on d.id=t.department_id
            left join employees ae on ae.id=t.assignee_employee_id
            left join employees se on se.id=t.supervisor_employee_id
            join pm_workflow_statuses ws on ws.id=t.status_id
            where t.company_id=:companyId and t.id=:taskId
            """, companyId, Map.of("taskId", taskId.toString()));
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        return result.get(0);
    }

    @Transactional
    public CreatedResource createTask(UUID companyId, TaskCreateRequest request, String actor) {
        if (request.projectId() != null) ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        if (request.sprintId() != null) ensureCompanyResource("pm_sprints", companyId, request.sprintId(), "Sprint");
        if (request.departmentId() != null) ensureCompanyResource("departments", companyId, request.departmentId(), "Department");
        if (request.assigneeEmployeeId() != null) ensureCompanyResource("employees", companyId, request.assigneeEmployeeId(), "Employee");
        if (request.supervisorEmployeeId() != null) ensureCompanyResource("employees", companyId, request.supervisorEmployeeId(), "Employee");
        if (request.parentTaskId() != null) ensureCompanyResource("pm_tasks", companyId, request.parentTaskId(), "Parent task");
        if (request.sprintId() != null) ensureSprintProject(companyId, request.sprintId(), request.projectId());
        if (request.parentTaskId() != null && request.projectId() != null)
            ensureTaskProject(companyId, request.parentTaskId(), request.projectId(), "Parent task");
        if (request.startsOn() != null && request.dueDate() != null && request.dueDate().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task due date must be on or after start date");
        UUID statusId = request.statusId() != null ? request.statusId() : defaultStatusId(companyId);
        ensureCompanyResource("pm_workflow_statuses", companyId, statusId, "Workflow status");
        UUID id = UUID.randomUUID();
        String code = request.code() == null || request.code().isBlank()
            ? nextCode("pm_tasks", "TSK", companyId) : request.code().trim();
        jdbc.sql("""
            insert into pm_tasks(id,company_id,code,title,description,project_id,sprint_id,department_id,
              assignee_employee_id,supervisor_employee_id,parent_task_id,status_id,priority,progress_percent,
              estimated_hours,starts_on,due_date,watcher_subjects)
            values(:id,:companyId,:code,:title,:description,:projectId,:sprintId,:departmentId,
              :assigneeId,:supervisorId,:parentId,:statusId,:priority,0,
              :estimatedHours,:startsOn,:dueDate,:watchers)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("title", request.title()).param("description", blankToNull(request.description()))
            .param("projectId", uuid(request.projectId())).param("sprintId", uuid(request.sprintId()))
            .param("departmentId", uuid(request.departmentId())).param("assigneeId", uuid(request.assigneeEmployeeId()))
            .param("supervisorId", uuid(request.supervisorEmployeeId())).param("parentId", uuid(request.parentTaskId()))
            .param("statusId", statusId.toString()).param("priority", request.priority() == null ? "MEDIUM" : request.priority())
            .param("estimatedHours", request.estimatedHours()).param("startsOn", request.startsOn())
            .param("dueDate", request.dueDate()).param("watchers", jsonArray(request.watcherSubjects())).update();
        if (request.checklist() != null) {
            int position = 0;
            for (ChecklistItemRequest item : request.checklist()) {
                jdbc.sql("""
                    insert into pm_task_checklist_items(id,company_id,task_id,title,position,done)
                    values(:id,:companyId,:taskId,:title,:position,false)
                    """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
                    .param("taskId", id.toString()).param("title", item.title()).param("position", position++).update();
            }
        }
        audit.write(companyId, actor, "PM_TASK_CREATED", "PM_TASK", id.toString(), "{\"code\":\"" + code + "\"}");
        return new CreatedResource(id, "TODO");
    }

    @Transactional
    public Map<String, Object> updateTask(UUID companyId, UUID taskId, TaskUpdateRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, taskId, "Task");
        if (request.projectId() != null) ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        if (request.sprintId() != null) ensureCompanyResource("pm_sprints", companyId, request.sprintId(), "Sprint");
        if (request.departmentId() != null) ensureCompanyResource("departments", companyId, request.departmentId(), "Department");
        if (request.assigneeEmployeeId() != null) ensureCompanyResource("employees", companyId, request.assigneeEmployeeId(), "Employee");
        if (request.supervisorEmployeeId() != null) ensureCompanyResource("employees", companyId, request.supervisorEmployeeId(), "Employee");
        if (request.sprintId() != null) {
            UUID projectId = request.projectId() != null ? request.projectId() : taskProject(companyId, taskId);
            ensureSprintProject(companyId, request.sprintId(), projectId);
        }
        if (request.startsOn() != null && request.dueDate() != null && request.dueDate().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task due date must be on or after start date");
        jdbc.sql("""
            update pm_tasks set title=coalesce(:title,title), description=coalesce(:description,description),
              project_id=coalesce(:projectId,project_id), sprint_id=coalesce(:sprintId,sprint_id),
              department_id=coalesce(:departmentId,department_id),
              assignee_employee_id=coalesce(:assigneeId,assignee_employee_id),
              supervisor_employee_id=coalesce(:supervisorId,supervisor_employee_id),
              priority=coalesce(:priority,priority), estimated_hours=coalesce(:estimatedHours,estimated_hours),
              starts_on=coalesce(:startsOn,starts_on), due_date=coalesce(:dueDate,due_date),
              watcher_subjects=coalesce(:watchers,watcher_subjects)
            where id=:id and company_id=:companyId
            """).param("title", blankToNull(request.title())).param("description", blankToNull(request.description()))
            .param("projectId", uuid(request.projectId())).param("sprintId", uuid(request.sprintId()))
            .param("departmentId", uuid(request.departmentId())).param("assigneeId", uuid(request.assigneeEmployeeId()))
            .param("supervisorId", uuid(request.supervisorEmployeeId())).param("priority", blankToNull(request.priority()))
            .param("estimatedHours", request.estimatedHours()).param("startsOn", request.startsOn())
            .param("dueDate", request.dueDate()).param("watchers", request.watcherSubjects() == null ? null : jsonArray(request.watcherSubjects()))
            .param("id", taskId.toString()).param("companyId", companyId.toString()).update();
        if (request.statusId() != null) moveTask(companyId, taskId, request.statusId(), null, actor);
        audit.write(companyId, actor, "PM_TASK_UPDATED", "PM_TASK", taskId.toString(), "{}");
        return task(companyId, taskId);
    }

    @Transactional
    public CreatedResource moveTask(UUID companyId, UUID taskId, UUID statusId, UUID sprintId, String actor) {
        ensureCompanyResource("pm_tasks", companyId, taskId, "Task");
        ensureCompanyResource("pm_workflow_statuses", companyId, statusId, "Workflow status");
        if (sprintId != null) {
            ensureCompanyResource("pm_sprints", companyId, sprintId, "Sprint");
            ensureSprintProject(companyId, sprintId, taskProject(companyId, taskId));
        }
        boolean isDone = jdbc.sql("select is_done from pm_workflow_statuses where id=:id and company_id=:companyId")
            .param("id", statusId.toString()).param("companyId", companyId.toString())
            .query(Boolean.class).optional().orElse(false);
        jdbc.sql("""
            update pm_tasks set status_id=:statusId, sprint_id=coalesce(:sprintId,sprint_id),
              progress_percent=case when :isDone then 100 else progress_percent end
            where id=:id and company_id=:companyId
            """).param("statusId", statusId.toString()).param("sprintId", uuid(sprintId))
            .param("isDone", isDone).param("id", taskId.toString()).param("companyId", companyId.toString()).update();
        refreshProjectProgress(companyId, taskId);
        audit.write(companyId, actor, "PM_TASK_MOVED", "PM_TASK", taskId.toString(),
            "{\"statusId\":\"" + statusId + "\"}");
        return new CreatedResource(taskId, isDone ? "DONE" : "MOVED");
    }

    @Transactional
    public CreatedResource addChecklistItem(UUID companyId, UUID taskId, ChecklistItemRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, taskId, "Task");
        UUID id = UUID.randomUUID();
        Long position = jdbc.sql("select coalesce(max(position),-1)+1 from pm_task_checklist_items where task_id=:taskId")
            .param("taskId", taskId.toString()).query(Long.class).single();
        jdbc.sql("""
            insert into pm_task_checklist_items(id,company_id,task_id,title,position,done)
            values(:id,:companyId,:taskId,:title,:position,false)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("taskId", taskId.toString()).param("title", request.title()).param("position", position).update();
        audit.write(companyId, actor, "PM_TASK_CHECKLIST_ADDED", "PM_TASK", taskId.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    @Transactional
    public CreatedResource toggleChecklistItem(UUID companyId, UUID taskId, ChecklistToggleRequest request, String actor) {
        ensureCompanyResource("pm_task_checklist_items", companyId, request.itemId(), "Checklist item");
        jdbc.sql("update pm_task_checklist_items set done=:done where id=:id and task_id=:taskId and company_id=:companyId")
            .param("done", request.done()).param("id", request.itemId().toString())
            .param("taskId", taskId.toString()).param("companyId", companyId.toString()).update();
        syncTaskProgressFromChecklist(companyId, taskId);
        audit.write(companyId, actor, "PM_TASK_CHECKLIST_TOGGLED", "PM_TASK", taskId.toString(), "{}");
        return new CreatedResource(taskId, request.done() ? "DONE" : "PENDING");
    }

    @Transactional
    public CreatedResource addTaskComment(UUID companyId, TaskCommentRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into pm_task_comments(id,company_id,task_id,author_subject,body) values(:id,:companyId,:taskId,:actor,:body)")
            .param("id", id.toString()).param("companyId", companyId.toString())
            .param("taskId", request.taskId().toString()).param("actor", actor).param("body", request.body()).update();
        audit.write(companyId, actor, "PM_TASK_COMMENT_ADDED", "PM_TASK", request.taskId().toString(), "{}");
        return new CreatedResource(id, "COMMENT");
    }

    @Transactional
    public CreatedResource addTaskAttachment(UUID companyId, TaskAttachmentRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_task_attachments(id,company_id,task_id,display_name,object_key,classification,uploaded_by_subject)
            values(:id,:companyId,:taskId,:displayName,:objectKey,:classification,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("taskId", request.taskId().toString()).param("displayName", request.displayName())
            .param("objectKey", request.objectKey())
            .param("classification", request.classification() == null ? "INTERNAL" : request.classification())
            .param("actor", actor).update();
        audit.write(companyId, actor, "PM_TASK_ATTACHMENT_ADDED", "PM_TASK", request.taskId().toString(), "{}");
        return new CreatedResource(id, "ATTACHMENT");
    }

    @Transactional
    public CreatedResource addTaskWatcher(UUID companyId, TaskWatcherRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        String current = jdbc.sql("select watcher_subjects from pm_tasks where id=:id and company_id=:companyId")
            .param("id", request.taskId().toString()).param("companyId", companyId.toString())
            .query(String.class).optional().orElse(null);
        List<String> watchers = parseJsonArray(current);
        if (!watchers.contains(request.watcherSubject())) {
            watchers.add(request.watcherSubject());
            jdbc.sql("update pm_tasks set watcher_subjects=:watchers where id=:id and company_id=:companyId")
                .param("watchers", jsonArray(watchers)).param("id", request.taskId().toString())
                .param("companyId", companyId.toString()).update();
        }
        audit.write(companyId, actor, "PM_TASK_WATCHER_ADDED", "PM_TASK", request.taskId().toString(), "{}");
        return new CreatedResource(request.taskId(), "WATCHER");
    }

    @Transactional
    public CreatedResource addTaskDependency(UUID companyId, TaskDependencyRequest request, String actor) {
        ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        ensureCompanyResource("pm_tasks", companyId, request.dependsOnTaskId(), "Dependency task");
        if (request.taskId().equals(request.dependsOnTaskId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A task cannot depend on itself");
        UUID taskProject = taskProject(companyId, request.taskId());
        UUID dependencyProject = taskProject(companyId, request.dependsOnTaskId());
        if (!java.util.Objects.equals(taskProject, dependencyProject))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task dependencies must belong to the same project");
        if (dependsOn(companyId, request.taskId(), request.dependsOnTaskId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dependency would create a cycle");
        jdbc.sql("""
            insert into pm_task_dependencies(task_id,company_id,depends_on_task_id,dependency_type)
            values(:taskId,:companyId,:dependsOn,:type)
            on conflict (task_id,depends_on_task_id) do update set dependency_type=excluded.dependency_type
            """).param("taskId", request.taskId().toString()).param("companyId", companyId.toString())
            .param("dependsOn", request.dependsOnTaskId().toString())
            .param("type", request.dependencyType() == null ? "FINISH_TO_START" : request.dependencyType()).update();
        audit.write(companyId, actor, "PM_TASK_DEPENDENCY_ADDED", "PM_TASK", request.taskId().toString(),
            "{\"dependsOn\":\"" + request.dependsOnTaskId() + "\"}");
        return new CreatedResource(request.taskId(), "DEPENDENCY");
    }

    public List<Map<String, Object>> taskChecklist(UUID companyId, UUID taskId) {
        return rows("select id,title,position,done from pm_task_checklist_items where company_id=:companyId and task_id=:taskId order by position",
            companyId, Map.of("taskId", taskId.toString()));
    }

    public List<Map<String, Object>> taskComments(UUID companyId, UUID taskId) {
        return rows("select id,author_subject,body,created_at from pm_task_comments where company_id=:companyId and task_id=:taskId order by created_at",
            companyId, Map.of("taskId", taskId.toString()));
    }

    public List<Map<String, Object>> taskAttachments(UUID companyId, UUID taskId) {
        return rows("select id,display_name,object_key,classification,uploaded_by_subject,created_at from pm_task_attachments where company_id=:companyId and task_id=:taskId order by created_at",
            companyId, Map.of("taskId", taskId.toString()));
    }

    public List<Map<String, Object>> taskDependencies(UUID companyId, UUID taskId) {
        return rows("""
            select d.task_id,d.depends_on_task_id,t.code,t.title,t.due_date,d.dependency_type
            from pm_task_dependencies d join pm_tasks t on t.id=d.depends_on_task_id
            where d.company_id=:companyId and d.task_id=:taskId
            """, companyId, Map.of("taskId", taskId.toString()));
    }

    public List<Map<String, Object>> taskSubtasks(UUID companyId, UUID taskId) {
        return rows("""
            select t.id,t.code,t.title,t.priority,t.due_date,ws.code status_code,ws.name status_name
            from pm_tasks t join pm_workflow_statuses ws on ws.id=t.status_id
            where t.company_id=:companyId and t.parent_task_id=:taskId order by t.code
            """, companyId, Map.of("taskId", taskId.toString()));
    }

    public List<Map<String, Object>> taskActivity(UUID companyId, UUID taskId) {
        return rows("""
            select id,actor_subject,action,resource_type,resource_id,metadata,created_at from audit_logs
            where company_id=:companyId and resource_id=:taskId order by created_at desc
            """, companyId, Map.of("taskId", taskId.toString()));
    }

    // ===== Timesheet =====

    public List<Map<String, Object>> timesheets(UUID companyId, UUID employeeId, UUID projectId, UUID taskId,
            LocalDate from, LocalDate to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        StringBuilder filter = new StringBuilder(" where ts.company_id=:companyId");
        if (employeeId != null) { filter.append(" and ts.employee_id=:employeeId"); params.put("employeeId", employeeId.toString()); }
        if (projectId != null) { filter.append(" and ts.project_id=:projectId"); params.put("projectId", projectId.toString()); }
        if (taskId != null) { filter.append(" and ts.task_id=:taskId"); params.put("taskId", taskId.toString()); }
        if (from != null) { filter.append(" and ts.work_date>=:from"); params.put("from", from); }
        if (to != null) { filter.append(" and ts.work_date<=:to"); params.put("to", to); }
        return rows("""
            select ts.id,ts.employee_id,e.full_name employee_name,e.employee_code,ts.task_id,t.code task_code,t.title task_title,
                   ts.project_id,p.code project_code,p.name project_name,ts.work_date,ts.hours,ts.status,ts.note,
                   ts.reviewer_subject,ts.reviewed_at,ts.created_at
            from pm_timesheets ts
            join employees e on e.id=ts.employee_id
            left join pm_tasks t on t.id=ts.task_id
            left join pm_projects p on p.id=ts.project_id""" + filter + " order by ts.work_date desc, e.full_name",
            companyId, params);
    }

    @Transactional
    public CreatedResource createTimesheet(UUID companyId, TimesheetRequest request, String actor) {
        UUID employeeId = request.employeeId() != null ? request.employeeId() : ownEmployeeId(companyId, actor);
        ensureCompanyResource("employees", companyId, employeeId, "Employee");
        if (request.taskId() != null) ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        if (request.projectId() != null) ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        if (request.taskId() != null && request.projectId() != null)
            ensureTaskProject(companyId, request.taskId(), request.projectId(), "Task");
        if (request.taskId() == null && request.projectId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A timesheet entry must reference a task or a project");
        StringBuilder duplicateSql = new StringBuilder("""
            select count(*) from pm_timesheets
            where company_id=:companyId and employee_id=:employeeId and work_date=:workDate
            """);
        if (request.taskId() == null) duplicateSql.append(" and task_id is null");
        else duplicateSql.append(" and task_id=:taskId");
        if (request.projectId() == null) duplicateSql.append(" and project_id is null");
        else duplicateSql.append(" and project_id=:projectId");
        var duplicateStatement = jdbc.sql(duplicateSql.toString())
            .param("companyId", companyId.toString()).param("employeeId", employeeId.toString())
            .param("workDate", request.workDate());
        if (request.taskId() != null) duplicateStatement = duplicateStatement.param("taskId", request.taskId().toString());
        if (request.projectId() != null) duplicateStatement = duplicateStatement.param("projectId", request.projectId().toString());
        long duplicate = duplicateStatement.query(Long.class).single();
        if (duplicate > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A timesheet entry already exists for this day");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into pm_timesheets(id,company_id,employee_id,task_id,project_id,work_date,hours,status,note)
            values(:id,:companyId,:employeeId,:taskId,:projectId,:workDate,:hours,'SUBMITTED',:note)
            """).param("id", id.toString()).param("companyId", companyId.toString())
            .param("employeeId", employeeId.toString()).param("taskId", uuid(request.taskId()))
            .param("projectId", uuid(request.projectId())).param("workDate", request.workDate())
            .param("hours", request.hours()).param("note", blankToNull(request.note())).update();
        audit.write(companyId, actor, "PM_TIMESHEET_CREATED", "PM_TIMESHEET", id.toString(),
            "{\"hours\":" + request.hours() + "}");
        return new CreatedResource(id, "SUBMITTED");
    }

    @Transactional
    public CreatedResource reviewTimesheet(UUID companyId, UUID entryId, TimesheetReviewRequest request, String actor) {
        ensureCompanyResource("pm_timesheets", companyId, entryId, "Timesheet");
        String status = "APPROVED".equals(request.decision()) ? "APPROVED" : "REJECTED";
        jdbc.sql("""
            update pm_timesheets set status=:status, reviewer_subject=:actor, reviewed_at=current_timestamp(6), note=coalesce(:note,note)
            where id=:id and company_id=:companyId
            """).param("status", status).param("actor", actor).param("note", blankToNull(request.note()))
            .param("id", entryId.toString()).param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_TIMESHEET_REVIEWED", "PM_TIMESHEET", entryId.toString(),
            "{\"status\":\"" + status + "\"}");
        return new CreatedResource(entryId, status);
    }

    // ===== Workload =====

    public List<Map<String, Object>> workload(UUID companyId, UUID employeeId, LocalDate from, LocalDate to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        StringBuilder filter = new StringBuilder(" where e.company_id=:companyId and e.employment_status='ACTIVE'");
        if (employeeId != null) { filter.append(" and e.id=:employeeId"); params.put("employeeId", employeeId.toString()); }
        String plannedWindow = "";
        if (from != null) { plannedWindow += " and t.due_date>=:fromDate"; params.put("fromDate", from); }
        if (to != null) { plannedWindow += " and t.due_date<=:toDate"; params.put("toDate", to); }
        String actualWindow = "";
        if (from != null) { actualWindow += " and ts.work_date>=:fromDate"; }
        if (to != null) { actualWindow += " and ts.work_date<=:toDate"; }
        return rows("""
            select e.id employee_id,e.full_name,e.employee_code,d.name department_name,
              coalesce(cap.weekly_capacity_hours, shift.daily_hours*5) capacity_hours,
              coalesce((select sum(t.estimated_hours) from pm_tasks t
                 where t.company_id=e.company_id and t.assignee_employee_id=e.id
                   and t.due_date is not null
                   and t.status_id not in (select id from pm_workflow_statuses where is_done=true)"""
            + plannedWindow + "),0) planned_hours,"
            + """
              coalesce((select sum(ts.hours) from pm_timesheets ts
                 where ts.company_id=e.company_id and ts.employee_id=e.id and ts.status='APPROVED'"""
            + actualWindow + "),0) actual_hours,"
            + """
              (select count(*) from pm_tasks t2
                 where t2.company_id=e.company_id and t2.assignee_employee_id=e.id
                   and t2.due_date is not null and t2.status_id not in (select id from pm_workflow_statuses where is_done=true)) open_tasks,
              (select count(*) from pm_tasks t3
                 where t3.company_id=e.company_id and t3.assignee_employee_id=e.id
                   and t3.due_date is not null and t3.due_date<current_date
                   and t3.status_id not in (select id from pm_workflow_statuses where is_done=true)) overdue_tasks
            from employees e
            left join departments d on d.id=e.department_id
            left join pm_employee_capacity cap on cap.employee_id=e.id and cap.company_id=e.company_id
            left join (select s.company_id,
                        sum((extract(epoch from (s.end_time-s.start_time))-s.break_minutes*60)/3600) daily_hours
                       from work_shifts s where s.active=true group by s.company_id) shift on shift.company_id=e.company_id"""
            + filter + " order by e.full_name", companyId, params);
    }

    @Transactional
    public CreatedResource setCapacity(UUID companyId, CapacityRequest request, String actor) {
        ensureCompanyResource("employees", companyId, request.employeeId(), "Employee");
        jdbc.sql("""
            insert into pm_employee_capacity(employee_id,company_id,weekly_capacity_hours)
            values(:employeeId,:companyId,:hours)
            on conflict (employee_id) do update set weekly_capacity_hours=excluded.weekly_capacity_hours
            """).param("employeeId", request.employeeId().toString()).param("companyId", companyId.toString())
            .param("hours", request.weeklyCapacityHours()).update();
        audit.write(companyId, actor, "PM_CAPACITY_SET", "PM_EMPLOYEE_CAPACITY", request.employeeId().toString(),
            "{\"hours\":" + request.weeklyCapacityHours() + "}");
        return new CreatedResource(request.employeeId(), "SET");
    }

    public List<Map<String, Object>> workloadConflicts(UUID companyId, UUID employeeId, LocalDate from, LocalDate to) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        StringBuilder filter = new StringBuilder(" where t.company_id=:companyId and t.due_date is not null"
            + " and t.status_id not in (select id from pm_workflow_statuses where is_done=true)");
        if (employeeId != null) { filter.append(" and t.assignee_employee_id=:employeeId"); params.put("employeeId", employeeId.toString()); }
        if (from != null) { filter.append(" and t.due_date>=:from"); params.put("from", from); }
        if (to != null) { filter.append(" and t.due_date<=:to"); params.put("to", to); }
        return rows("""
            select t.id,t.code,t.title,t.starts_on,t.due_date,t.estimated_hours,e.full_name assignee_name,
                   t.assignee_employee_id, l.starts_on leave_from, l.ends_on leave_to
            from pm_tasks t
            join employees e on e.id=t.assignee_employee_id
            left join leave_requests l on l.employee_id=t.assignee_employee_id and l.status='APPROVED'
                 and l.starts_on<=t.due_date and l.ends_on>=t.starts_on""" + filter
            + " order by t.due_date", companyId, params);
    }

    // ===== Project Reports =====

    public Map<String, Object> report(UUID companyId, UUID projectId) {
        ensureCompanyResource("pm_projects", companyId, projectId, "Project");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project(companyId, projectId));
        result.put("progress", rows("""
            select count(*) total_tasks,
                   sum(case when ws.is_done then 1 else 0 end) done_tasks,
                   coalesce(round(avg(t.progress_percent),2),0) avg_progress
            from pm_tasks t join pm_workflow_statuses ws on ws.id=t.status_id
            where t.company_id=:companyId and t.project_id=:projectId
            """, companyId, Map.of("projectId", projectId.toString())));
        result.put("time", rows("""
            select coalesce((select sum(t.estimated_hours) from pm_tasks t
                    where t.company_id=:companyId and t.project_id=:projectId),0) estimated_hours,
                   coalesce((select sum(ts.hours) from pm_timesheets ts
                    where ts.company_id=:companyId and ts.status='APPROVED'
                      and (ts.project_id=:projectId or ts.task_id in
                        (select t2.id from pm_tasks t2 where t2.company_id=:companyId and t2.project_id=:projectId))),0) actual_hours,
                   coalesce((select sum(ts.hours) from pm_timesheets ts
                    where ts.company_id=:companyId
                      and (ts.project_id=:projectId or ts.task_id in
                        (select t3.id from pm_tasks t3 where t3.company_id=:companyId and t3.project_id=:projectId))),0) logged_hours
            """, companyId, Map.of("projectId", projectId.toString())));
        result.put("cost", rows("""
            select coalesce(sum(ts.hours),0) approved_hours,
                   coalesce(sum(ts.hours*m.cost_rate),0) total_cost,
                   coalesce(sum(ts.hours*m.cost_rate)/nullif(count(distinct ts.employee_id),0),0) avg_cost_per_member
            from pm_timesheets ts
            join pm_project_members m on m.company_id=ts.company_id and m.project_id=:projectId and m.employee_id=ts.employee_id
            where ts.company_id=:companyId and ts.status='APPROVED'
              and (ts.project_id=:projectId or ts.task_id in
                (select t.id from pm_tasks t where t.company_id=:companyId and t.project_id=:projectId))
            """, companyId, Map.of("projectId", projectId.toString())));
        result.put("staleTasks", rows("""
            select t.id,t.code,t.title,t.due_date,e.full_name assignee_name
            from pm_tasks t left join employees e on e.id=t.assignee_employee_id
            where t.company_id=:companyId and t.project_id=:projectId and t.due_date<current_date
              and t.status_id not in (select id from pm_workflow_statuses where is_done=true)
            order by t.due_date
            """, companyId, Map.of("projectId", projectId.toString())));
        result.put("risks", risks(companyId, projectId));
        result.put("members", rows("""
            select m.employee_id,e.full_name,e.employee_code,m.role,m.cost_rate,
                   (select count(*) from pm_tasks t where t.company_id=m.company_id and t.project_id=m.project_id
                      and t.assignee_employee_id=m.employee_id) assigned_tasks,
                   (select count(*) from pm_tasks t where t.company_id=m.company_id and t.project_id=m.project_id
                      and t.assignee_employee_id=m.employee_id and t.status_id in (select id from pm_workflow_statuses where is_done=true)) done_tasks,
                   (select coalesce(sum(ts.hours),0) from pm_timesheets ts where ts.company_id=m.company_id
                      and ts.employee_id=m.employee_id and ts.status='APPROVED'
                      and (ts.project_id=m.project_id or ts.task_id in
                        (select pt.id from pm_tasks pt where pt.company_id=m.company_id and pt.project_id=m.project_id))) approved_hours
            from pm_project_members m join employees e on e.id=m.employee_id
            where m.company_id=:companyId and m.project_id=:projectId order by e.full_name
            """, companyId, Map.of("projectId", projectId.toString())));
        result.put("openIssues", rows("""
            select id,code,title,severity,status,type from pm_issues
            where company_id=:companyId and project_id=:projectId and status in ('OPEN','IN_PROGRESS','REOPENED') order by created_at desc
            """, companyId, Map.of("projectId", projectId.toString())));
        return result;
    }

    // ===== Issue Management =====

    public List<Map<String, Object>> issues(UUID companyId, UUID projectId, String type, String severity, String status) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        StringBuilder filter = new StringBuilder(" where i.company_id=:companyId");
        if (projectId != null) { filter.append(" and i.project_id=:projectId"); params.put("projectId", projectId.toString()); }
        if (type != null && !type.isBlank()) { filter.append(" and i.type=:type"); params.put("type", type); }
        if (severity != null && !severity.isBlank()) { filter.append(" and i.severity=:severity"); params.put("severity", severity); }
        if (status != null && !status.isBlank()) { filter.append(" and i.status=:status"); params.put("status", status); }
        return rows("""
            select i.id,i.code,i.type,i.severity,i.status,i.title,i.description,i.project_id,p.code project_code,
                   i.task_id,t.code task_code,i.reporter_subject,i.assignee_employee_id,e.full_name assignee_name,
                   i.resolution_note,i.resolved_at,i.created_at
            from pm_issues i
            left join pm_projects p on p.id=i.project_id
            left join pm_tasks t on t.id=i.task_id
            left join employees e on e.id=i.assignee_employee_id""" + filter + " order by i.created_at desc",
            companyId, params);
    }

    public List<Map<String, Object>> issues(UUID companyId, UUID projectId, String type, String severity, String status,
            Authentication authentication) {
        return issues(companyId, projectId, type, severity, status).stream()
            .filter(item -> access.canAccessIssue(authentication, companyId, uuidValue(item.get("id")), "pm.issue.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createIssue(UUID companyId, IssueCreateRequest request, String actor) {
        if (request.projectId() != null) ensureCompanyResource("pm_projects", companyId, request.projectId(), "Project");
        if (request.taskId() != null) ensureCompanyResource("pm_tasks", companyId, request.taskId(), "Task");
        if (request.assigneeEmployeeId() != null) ensureCompanyResource("employees", companyId, request.assigneeEmployeeId(), "Employee");
        if (request.taskId() != null && request.projectId() != null)
            ensureTaskProject(companyId, request.taskId(), request.projectId(), "Task");
        UUID id = UUID.randomUUID();
        String code = request.code() == null || request.code().isBlank()
            ? nextCode("pm_issues", "ISS", companyId) : request.code().trim();
        jdbc.sql("""
            insert into pm_issues(id,company_id,code,type,severity,status,title,description,project_id,task_id,
              reporter_subject,assignee_employee_id)
            values(:id,:companyId,:code,:type,:severity,'OPEN',:title,:description,:projectId,:taskId,:actor,:assigneeId)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("type", request.type()).param("severity", request.severity() == null ? "MEDIUM" : request.severity())
            .param("title", request.title()).param("description", blankToNull(request.description()))
            .param("projectId", uuid(request.projectId())).param("taskId", uuid(request.taskId()))
            .param("actor", actor).param("assigneeId", uuid(request.assigneeEmployeeId())).update();
        audit.write(companyId, actor, "PM_ISSUE_CREATED", "PM_ISSUE", id.toString(), "{\"code\":\"" + code + "\"}");
        return new CreatedResource(id, "OPEN");
    }

    @Transactional
    public CreatedResource decideIssue(UUID companyId, IssueDecisionRequest request, String actor) {
        ensureCompanyResource("pm_issues", companyId, request.issueId(), "Issue");
        jdbc.sql("""
            update pm_issues set status=:status,
              resolved_at=case when :status in ('RESOLVED','CLOSED') then current_timestamp(6) else resolved_at end
            where id=:id and company_id=:companyId
            """).param("status", request.status()).param("id", request.issueId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_ISSUE_STATUS_CHANGED", "PM_ISSUE", request.issueId().toString(),
            "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(request.issueId(), request.status());
    }

    @Transactional
    public CreatedResource resolveIssue(UUID companyId, IssueResolveRequest request, String actor) {
        ensureCompanyResource("pm_issues", companyId, request.issueId(), "Issue");
        jdbc.sql("""
            update pm_issues set status='RESOLVED', resolution_note=:note, resolved_at=current_timestamp(6)
            where id=:id and company_id=:companyId
            """).param("note", blankToNull(request.resolutionNote())).param("id", request.issueId().toString())
            .param("companyId", companyId.toString()).update();
        audit.write(companyId, actor, "PM_ISSUE_RESOLVED", "PM_ISSUE", request.issueId().toString(), "{}");
        return new CreatedResource(request.issueId(), "RESOLVED");
    }

    public List<Map<String, Object>> issueHistory(UUID companyId, UUID issueId) {
        return rows("""
            select id,actor_subject,action,resource_type,resource_id,metadata,created_at from audit_logs
            where company_id=:companyId and resource_id=:issueId order by created_at desc
            """, companyId, Map.of("issueId", issueId.toString()));
    }

    // ===== Internals =====

    private void ensureSprintProject(UUID companyId, UUID sprintId, UUID projectId) {
        if (projectId == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A sprint task must reference its project");
        long found = jdbc.sql("select count(*) from pm_sprints where company_id=:companyId and id=:sprintId and project_id=:projectId")
            .param("companyId", companyId.toString()).param("sprintId", sprintId.toString())
            .param("projectId", projectId.toString()).query(Long.class).single();
        if (found == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sprint does not belong to project");
    }

    private void ensureTaskProject(UUID companyId, UUID taskId, UUID projectId, String label) {
        long found = jdbc.sql("select count(*) from pm_tasks where company_id=:companyId and id=:taskId and project_id=:projectId")
            .param("companyId", companyId.toString()).param("taskId", taskId.toString())
            .param("projectId", projectId.toString()).query(Long.class).single();
        if (found == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " does not belong to project");
    }

    private UUID taskProject(UUID companyId, UUID taskId) {
        return jdbc.sql("select project_id from pm_tasks where company_id=:companyId and id=:taskId")
            .param("companyId", companyId.toString()).param("taskId", taskId.toString())
            .query(String.class).optional().map(UUID::fromString).orElse(null);
    }

    private void refreshProjectProgress(UUID companyId, UUID taskId) {
        String projectId = jdbc.sql("select project_id from pm_tasks where id=:id and company_id=:companyId")
            .param("id", taskId.toString()).param("companyId", companyId.toString())
            .query(String.class).optional().orElse(null);
        if (projectId == null) return;
        Double percent = jdbc.sql("""
            select coalesce(round(avg(t.progress_percent),2),0) from pm_tasks t
            where t.company_id=:companyId and t.project_id=:projectId
            """).param("companyId", companyId.toString()).param("projectId", projectId).query(Double.class).single();
        jdbc.sql("update pm_projects set progress_percent=:percent where id=:id and company_id=:companyId")
            .param("percent", percent).param("id", projectId).param("companyId", companyId.toString()).update();
    }

    private void syncTaskProgressFromChecklist(UUID companyId, UUID taskId) {
        Long total = jdbc.sql("select count(*) from pm_task_checklist_items where task_id=:taskId and company_id=:companyId")
            .param("taskId", taskId.toString()).param("companyId", companyId.toString()).query(Long.class).single();
        Long done = jdbc.sql("select count(*) from pm_task_checklist_items where task_id=:taskId and company_id=:companyId and done=true")
            .param("taskId", taskId.toString()).param("companyId", companyId.toString()).query(Long.class).single();
        double percent = total == 0 ? 0 : done * 100.0 / total;
        jdbc.sql("update pm_tasks set progress_percent=:percent where id=:id and company_id=:companyId")
            .param("percent", percent).param("id", taskId.toString()).param("companyId", companyId.toString()).update();
    }

    private boolean dependsOn(UUID companyId, UUID taskId, UUID candidateTaskId) {
        List<String> parents = jdbc.sql("select depends_on_task_id from pm_task_dependencies where task_id=:taskId and company_id=:companyId")
            .param("taskId", candidateTaskId.toString()).param("companyId", companyId.toString())
            .query(String.class).list();
        for (String parent : parents) {
            if (parent.equals(taskId.toString())) return true;
            if (dependsOn(companyId, taskId, UUID.fromString(parent))) return true;
        }
        return false;
    }

    private UUID defaultStatusId(UUID companyId) {
        ensureDefaultWorkflowStatuses(companyId);
        return jdbc.sql("""
            select id from pm_workflow_statuses where company_id=:companyId and workflow_type='TASK'
            order by sort_order limit 1
            """).param("companyId", companyId.toString()).query(String.class).optional()
            .map(UUID::fromString)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No workflow status configured"));
    }

    /**
     * Companies created after the Flyway seed have no workflow statuses, which would make task
     * creation impossible because pm_tasks.status_id is mandatory. Provision the defaults on demand.
     */
    @Transactional
    public void ensureDefaultWorkflowStatuses(UUID companyId) {
        long existing = count("pm_workflow_statuses", "workflow_type='TASK'", companyId);
        if (existing > 0) return;
        Object[][] defaults = {
            {"BACKLOG", "Backlog", 0, "#6b7f8a", false},
            {"TODO", "Đến làm", 1, "#5b8def", false},
            {"IN_PROGRESS", "Đang thực hiện", 2, "#e0a52c", false},
            {"IN_REVIEW", "Đang xem xét", 3, "#9b6bd6", false},
            {"DONE", "Hoàn thành", 4, "#43a477", true},
        };
        for (Object[] status : defaults) {
            jdbc.sql("""
                insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
                values(:id,:companyId,'TASK',:code,:name,:sortOrder,:color,:isDone,true)
                """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
                .param("code", status[0]).param("name", status[1]).param("sortOrder", status[2])
                .param("color", status[3]).param("isDone", status[4]).update();
        }
    }

    private String nextCode(String table, String prefix, UUID companyId) {
        long count = count(table, "1=1", companyId);
        String code;
        do {
            count++;
            code = prefix + "-" + String.format("%04d", count);
        } while (count(table, "code='" + code + "'", companyId) > 0);
        return code;
    }

    private ProjectView projectView(ResultSet rs, int row) throws SQLException {
        String department = rs.getString("department_id");
        BigDecimal budget = rs.getBigDecimal("budget");
        BigDecimal actualCost = rs.getBigDecimal("actual_cost");
        BigDecimal profit = budget == null || actualCost == null ? null : budget.subtract(actualCost);
        return new ProjectView(UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("name"),
            rs.getString("status"), department == null ? null : UUID.fromString(department), rs.getString("department_name"),
            budget, rs.getString("currency"), rs.getObject("starts_on", LocalDate.class), rs.getObject("ends_on", LocalDate.class),
            rs.getBigDecimal("progress_percent"), rs.getString("risk_level"),
            rs.getLong("member_count"), rs.getLong("task_count"), actualCost, profit);
    }

    private List<Map<String, Object>> rows(String sql, UUID companyId, Map<String, Object> params) {
        Map<String, Object> all = new LinkedHashMap<>();
        all.put("companyId", companyId.toString());
        all.putAll(params);
        return namedJdbc.queryForList(sql, all);
    }

    private long count(String table, String condition, UUID companyId) {
        return jdbc.sql("select count(*) from " + table + " where company_id=:companyId and " + condition)
            .param("companyId", companyId.toString()).query(Long.class).single();
    }

    private UUID ownEmployeeId(UUID companyId, String subject) {
        return jdbc.sql("select id from employees where company_id=:companyId and keycloak_subject=:subject and employment_status='ACTIVE'")
            .param("companyId", companyId.toString()).param("subject", subject).query(String.class).optional()
            .map(UUID::fromString)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No active employee profile is linked to this account"));
    }

    private void ensureCompanyResource(String table, UUID companyId, UUID id, String label) {
        long found = jdbc.sql("select count(*) from " + table + " where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", id.toString()).query(Long.class).single();
        if (found == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String uuid(UUID value) { return value == null ? null : value.toString(); }
    private static UUID uuidValue(Object value) {
        if (value == null) return null;
        return value instanceof UUID id ? id : UUID.fromString(value.toString().trim());
    }
    private static LocalDate localDateValue(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return null;
    }

    private static String jsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append('"').append(values.get(index).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return builder.append(']').toString();
    }

    private static List<String> parseJsonArray(String json) {
        List<String> values = new ArrayList<>();
        if (json == null || json.isBlank()) return values;
        for (String part : json.replace("[", "").replace("]", "").split(",")) {
            String cleaned = part.trim().replace("\"", "").replace("\\\"", "").replace("\\\\", "");
            if (!cleaned.isBlank()) values.add(cleaned);
        }
        return values;
    }

    private static boolean allowed(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission)
            || authority.getAuthority().equals("super-admin") || authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }
}
