package com.company.erp.pm;

import com.company.erp.pm.PmModels.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pm")
public class PmController {
    private final PmService pm;
    private final PmAccess pmAccess;

    public PmController(PmService pm, PmAccess pmAccess) { this.pm = pm; this.pmAccess = pmAccess; }

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.access')")
    PmSummary summary(@RequestParam UUID companyId, Authentication authentication) { return pm.summary(companyId, authentication); }

    // ===== Project Management =====

    @GetMapping("/projects")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.project.read')")
    List<ProjectView> projects(@RequestParam UUID companyId, Authentication authentication) {
        return pm.projects(companyId, authentication);
    }

    @GetMapping("/projects/{projectId}")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.read')")
    ProjectView project(@RequestParam UUID companyId, @PathVariable UUID projectId) { return pm.project(companyId, projectId); }

    @GetMapping("/projects/{projectId}/members")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.read')")
    List<Map<String, Object>> projectMembers(@RequestParam UUID companyId, @PathVariable UUID projectId) {
        return pm.projectMembers(companyId, projectId);
    }

    @GetMapping("/projects/{projectId}/milestones")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.read')")
    List<Map<String, Object>> milestones(@RequestParam UUID companyId, @PathVariable UUID projectId) {
        return pm.milestones(companyId, projectId);
    }

    @GetMapping("/projects/{projectId}/risks")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.read')")
    List<Map<String, Object>> risks(@RequestParam UUID companyId, @PathVariable UUID projectId) {
        return pm.risks(companyId, projectId);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.project.write')")
    CreatedResource createProject(@RequestParam UUID companyId, @Valid @RequestBody ProjectCreateRequest request,
            Authentication authentication) { return pm.createProject(companyId, request, authentication.getName()); }

    @PatchMapping("/projects/{projectId}")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.write')")
    ProjectView updateProject(@RequestParam UUID companyId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectUpdateRequest request, Authentication authentication) {
        return pm.updateProject(companyId, projectId, request, authentication.getName());
    }

    @PostMapping("/projects/{projectId}/decision")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.write')")
    CreatedResource decideProject(@RequestParam UUID companyId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDecisionRequest body, Authentication authentication) {
        return pm.decideProject(companyId, new ProjectDecisionRequest(projectId, body.status()), authentication.getName());
    }

    @PostMapping("/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.write')")
    CreatedResource addProjectMember(@RequestParam UUID companyId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectMemberRequest body, Authentication authentication) {
        return pm.addProjectMember(companyId, new ProjectMemberRequest(projectId, body.employeeId(), body.role(), body.costRate()),
            authentication.getName());
    }

    @PostMapping("/projects/{projectId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.write')")
    CreatedResource addMilestone(@RequestParam UUID companyId, @PathVariable UUID projectId,
            @Valid @RequestBody MilestoneRequest body, Authentication authentication) {
        return pm.addMilestone(companyId, new MilestoneRequest(projectId, body.title(), body.dueDate()), authentication.getName());
    }

    @PostMapping("/projects/{projectId}/risks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.project.write')")
    CreatedResource addRisk(@RequestParam UUID companyId, @PathVariable UUID projectId,
            @Valid @RequestBody RiskRequest body, Authentication authentication) {
        return pm.addRisk(companyId, new RiskRequest(projectId, body.title(), body.likelihood(), body.impact(),
            body.mitigation(), body.ownerEmployeeId()), authentication.getName());
    }

    // ===== Sprint & Workflow =====

    @GetMapping("/sprints")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.sprint.read')")
    List<Map<String, Object>> sprints(@RequestParam UUID companyId, @RequestParam(required = false) UUID projectId,
            Authentication authentication) {
        return pm.sprints(companyId, projectId, authentication);
    }

    @PostMapping("/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canCreateInProject(authentication,#companyId,#request.projectId(),'pm.sprint.write')")
    CreatedResource createSprint(@RequestParam UUID companyId, @Valid @RequestBody SprintRequest request,
            Authentication authentication) { return pm.createSprint(companyId, request, authentication.getName()); }

    @PostMapping("/sprints/{sprintId}/decision")
    @PreAuthorize("@pmAccess.canAccessSprint(authentication,#companyId,#sprintId,'pm.sprint.write')")
    CreatedResource decideSprint(@RequestParam UUID companyId, @PathVariable UUID sprintId,
            @Valid @RequestBody SprintDecisionRequest body, Authentication authentication) {
        return pm.decideSprint(companyId, new SprintDecisionRequest(sprintId, body.status()), authentication.getName());
    }

    @GetMapping("/workflow/statuses")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.sprint.read')")
    List<Map<String, Object>> workflowStatuses(@RequestParam UUID companyId, @RequestParam(required = false) String workflowType) {
        return pm.workflowStatuses(companyId, workflowType);
    }

    @PostMapping("/workflow/statuses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.workflow.manage')")
    CreatedResource createWorkflowStatus(@RequestParam UUID companyId, @Valid @RequestBody WorkflowStatusRequest request,
            Authentication authentication) { return pm.createWorkflowStatus(companyId, request, authentication.getName()); }

    // ===== Task Management =====

    @GetMapping("/tasks")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.task.read')")
    List<Map<String, Object>> tasks(@RequestParam UUID companyId,
            @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) UUID statusId, @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) String priority, @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, Authentication authentication) {
        return pm.tasks(companyId, projectId, sprintId, statusId, assigneeId, priority, from, to, authentication);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    Map<String, Object> task(@RequestParam UUID companyId, @PathVariable UUID taskId) { return pm.task(companyId, taskId); }

    @GetMapping("/tasks/{taskId}/checklist")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskChecklist(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskChecklist(companyId, taskId);
    }

    @GetMapping("/tasks/{taskId}/comments")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskComments(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskComments(companyId, taskId);
    }

    @GetMapping("/tasks/{taskId}/attachments")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskAttachments(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskAttachments(companyId, taskId);
    }

    @GetMapping("/tasks/{taskId}/dependencies")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskDependencies(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskDependencies(companyId, taskId);
    }

    @GetMapping("/tasks/{taskId}/subtasks")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskSubtasks(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskSubtasks(companyId, taskId);
    }

    @GetMapping("/tasks/{taskId}/activity")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.read')")
    List<Map<String, Object>> taskActivity(@RequestParam UUID companyId, @PathVariable UUID taskId) {
        return pm.taskActivity(companyId, taskId);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canCreateInProject(authentication,#companyId,#request.projectId(),'pm.task.write')")
    CreatedResource createTask(@RequestParam UUID companyId, @Valid @RequestBody TaskCreateRequest request,
            Authentication authentication) { return pm.createTask(companyId, request, authentication.getName()); }

    @PatchMapping("/tasks/{taskId}")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    Map<String, Object> updateTask(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskUpdateRequest request, Authentication authentication) {
        return pm.updateTask(companyId, taskId, request, authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/move")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource moveTask(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskMoveRequest body, Authentication authentication) {
        return pm.moveTask(companyId, taskId, body.statusId(), body.sprintId(), authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/checklist")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource addChecklistItem(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody ChecklistItemRequest request, Authentication authentication) {
        return pm.addChecklistItem(companyId, taskId, request, authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/checklist/{itemId}/toggle")
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource toggleChecklistItem(@RequestParam UUID companyId, @PathVariable UUID taskId, @PathVariable UUID itemId,
            @Valid @RequestBody ChecklistToggleRequest body, Authentication authentication) {
        return pm.toggleChecklistItem(companyId, taskId, new ChecklistToggleRequest(itemId, body.done()), authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource addTaskComment(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskCommentRequest request, Authentication authentication) {
        return pm.addTaskComment(companyId, new TaskCommentRequest(taskId, request.body()), authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource addTaskAttachment(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskAttachmentRequest request, Authentication authentication) {
        return pm.addTaskAttachment(companyId, new TaskAttachmentRequest(taskId, request.displayName(), request.objectKey(),
            request.classification()), authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/watchers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource addTaskWatcher(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskWatcherRequest request, Authentication authentication) {
        return pm.addTaskWatcher(companyId, new TaskWatcherRequest(taskId, request.watcherSubject()), authentication.getName());
    }

    @PostMapping("/tasks/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canAccessTask(authentication,#companyId,#taskId,'pm.task.write')")
    CreatedResource addTaskDependency(@RequestParam UUID companyId, @PathVariable UUID taskId,
            @Valid @RequestBody TaskDependencyRequest request, Authentication authentication) {
        return pm.addTaskDependency(companyId,
            new TaskDependencyRequest(taskId, request.dependsOnTaskId(), request.dependencyType()), authentication.getName());
    }

    // ===== Timesheet =====

    @GetMapping("/timesheets")
    @PreAuthorize("@pmAccess.canViewTimesheets(authentication,#companyId,#employeeId,#projectId,#taskId)")
    List<Map<String, Object>> timesheets(@RequestParam UUID companyId, @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID projectId, @RequestParam(required = false) UUID taskId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return pm.timesheets(companyId, employeeId, projectId, taskId, from, to);
    }

    @PostMapping("/timesheets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canCreateTimesheet(authentication,#companyId,#request.employeeId(),#request.projectId(),#request.taskId())")
    CreatedResource createTimesheet(@RequestParam UUID companyId, @Valid @RequestBody TimesheetRequest request,
            Authentication authentication) { return pm.createTimesheet(companyId, request, authentication.getName()); }

    @PostMapping("/timesheets/{entryId}/review")
    @PreAuthorize("@pmAccess.canReviewTimesheet(authentication,#companyId,#entryId)")
    CreatedResource reviewTimesheet(@RequestParam UUID companyId, @PathVariable UUID entryId,
            @Valid @RequestBody TimesheetReviewRequest request, Authentication authentication) {
        return pm.reviewTimesheet(companyId, entryId, request, authentication.getName());
    }

    // ===== Workload =====

    @GetMapping("/workload")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.workload.read')")
    List<Map<String, Object>> workload(@RequestParam UUID companyId, @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return pm.workload(companyId, employeeId, from, to);
    }

    @GetMapping("/workload/conflicts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.workload.read')")
    List<Map<String, Object>> workloadConflicts(@RequestParam UUID companyId, @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return pm.workloadConflicts(companyId, employeeId, from, to);
    }

    @PostMapping("/workload/capacity")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canManageCapacity(authentication,#companyId,#request.employeeId())")
    CreatedResource setCapacity(@RequestParam UUID companyId, @Valid @RequestBody CapacityRequest request,
            Authentication authentication) { return pm.setCapacity(companyId, request, authentication.getName()); }

    // ===== Project Reports =====

    @GetMapping("/reports/{projectId}")
    @PreAuthorize("@pmAccess.canAccessProject(authentication,#companyId,#projectId,'pm.report.read')")
    Map<String, Object> report(@RequestParam UUID companyId, @PathVariable UUID projectId) {
        return pm.report(companyId, projectId);
    }

    // ===== Issue Management =====

    @GetMapping("/issues")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'pm.issue.read')")
    List<Map<String, Object>> issues(@RequestParam UUID companyId, @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String type, @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status, Authentication authentication) {
        return pm.issues(companyId, projectId, type, severity, status, authentication);
    }

    @GetMapping("/issues/{issueId}/history")
    @PreAuthorize("@pmAccess.canAccessIssue(authentication,#companyId,#issueId,'pm.issue.read')")
    List<Map<String, Object>> issueHistory(@RequestParam UUID companyId, @PathVariable UUID issueId) {
        return pm.issueHistory(companyId, issueId);
    }

    @PostMapping("/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@pmAccess.canCreateInProject(authentication,#companyId,#request.projectId(),'pm.issue.write')")
    CreatedResource createIssue(@RequestParam UUID companyId, @Valid @RequestBody IssueCreateRequest request,
            Authentication authentication) { return pm.createIssue(companyId, request, authentication.getName()); }

    @PostMapping("/issues/{issueId}/decision")
    @PreAuthorize("@pmAccess.canAccessIssue(authentication,#companyId,#issueId,'pm.issue.write')")
    CreatedResource decideIssue(@RequestParam UUID companyId, @PathVariable UUID issueId,
            @Valid @RequestBody IssueDecisionRequest body, Authentication authentication) {
        return pm.decideIssue(companyId, new IssueDecisionRequest(issueId, body.status()), authentication.getName());
    }

    @PostMapping("/issues/{issueId}/resolve")
    @PreAuthorize("@pmAccess.canAccessIssue(authentication,#companyId,#issueId,'pm.issue.write')")
    CreatedResource resolveIssue(@RequestParam UUID companyId, @PathVariable UUID issueId,
            @Valid @RequestBody IssueResolveRequest body, Authentication authentication) {
        return pm.resolveIssue(companyId, new IssueResolveRequest(issueId, body.resolutionNote()), authentication.getName());
    }
}
