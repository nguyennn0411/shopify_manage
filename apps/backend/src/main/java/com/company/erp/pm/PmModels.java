package com.company.erp.pm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PmModels {
    private PmModels() {}

    // ===== Project Management =====

    public record ProjectCreateRequest(
        @Size(max = 60) String code,
        @NotBlank @Size(max = 255) String name,
        UUID departmentId,
        UUID ownerEmployeeId,
        @Size(max = 4000) String objective,
        @Size(max = 4000) String scope,
        BigDecimal budget,
        @Size(max = 3) String currency,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "PLANNED|ACTIVE|ON_HOLD|CLOSED|CANCELLED") String status,
        LocalDate startsOn,
        LocalDate endsOn,
        @Size(max = 20) String riskLevel) {}

    public record ProjectUpdateRequest(
        @Size(max = 255) String name,
        UUID departmentId,
        UUID ownerEmployeeId,
        @Size(max = 4000) String objective,
        @Size(max = 4000) String scope,
        BigDecimal budget,
        @Size(max = 3) String currency,
        LocalDate startsOn,
        LocalDate endsOn,
        @Size(max = 20) String riskLevel) {}

    public record ProjectDecisionRequest(
        UUID projectId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "PLANNED|ACTIVE|ON_HOLD|CLOSED|CANCELLED") String status) {}

    public record ProjectMemberRequest(
        @NotNull UUID projectId,
        @NotNull UUID employeeId,
        @Size(max = 30) String role,
        BigDecimal costRate) {}

    public record MilestoneRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 255) String title,
        @NotNull LocalDate dueDate) {}

    public record RiskRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 20) String likelihood,
        @Size(max = 20) String impact,
        @Size(max = 4000) String mitigation,
        UUID ownerEmployeeId) {}

    public record ProjectView(UUID id, String code, String name, String status, UUID departmentId,
        String departmentName, BigDecimal budget, String currency, LocalDate startsOn, LocalDate endsOn,
        BigDecimal progressPercent, String riskLevel, Long memberCount, Long taskCount,
        BigDecimal actualCost, BigDecimal profit) {}

    // ===== Sprint & Workflow =====

    public record SprintRequest(
        @NotNull UUID projectId,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 4000) String goal,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn) {}

    public record SprintDecisionRequest(
        UUID sprintId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "PLANNED|ACTIVE|CLOSED") String status) {}

    public record WorkflowStatusRequest(
        @NotBlank @Size(max = 20) String workflowType,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 120) String name,
        @NotNull Integer sortOrder,
        @Size(max = 20) String color,
        @NotNull Boolean isDone) {}

    // ===== Task Management =====

    public record TaskCreateRequest(
        @Size(max = 60) String code,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        UUID projectId,
        UUID sprintId,
        UUID departmentId,
        UUID assigneeEmployeeId,
        UUID supervisorEmployeeId,
        UUID parentTaskId,
        UUID statusId,
        @Size(max = 20) String priority,
        BigDecimal estimatedHours,
        LocalDate startsOn,
        LocalDate dueDate,
        List<String> watcherSubjects,
        @Valid List<ChecklistItemRequest> checklist) {}

    public record TaskUpdateRequest(
        @Size(max = 255) String title,
        @Size(max = 4000) String description,
        UUID projectId,
        UUID sprintId,
        UUID departmentId,
        UUID assigneeEmployeeId,
        UUID supervisorEmployeeId,
        UUID statusId,
        @Size(max = 20) String priority,
        BigDecimal estimatedHours,
        LocalDate startsOn,
        LocalDate dueDate,
        List<String> watcherSubjects) {}

    public record TaskMoveRequest(
        UUID taskId,
        @NotNull UUID statusId,
        UUID sprintId) {}

    public record ChecklistItemRequest(
        @NotBlank @Size(max = 255) String title) {}

    public record ChecklistToggleRequest(
        UUID itemId,
        @NotNull Boolean done) {}

    public record TaskCommentRequest(
        @NotNull UUID taskId,
        @NotBlank @Size(max = 4000) String body) {}

    public record TaskAttachmentRequest(
        @NotNull UUID taskId,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 500) String objectKey,
        @Size(max = 30) String classification) {}

    public record TaskWatcherRequest(
        @NotNull UUID taskId,
        @NotNull String watcherSubject) {}

    public record TaskDependencyRequest(
        @NotNull UUID taskId,
        @NotNull UUID dependsOnTaskId,
        @Size(max = 30) String dependencyType) {}

    // ===== Timesheet =====

    public record TimesheetRequest(
        UUID employeeId,
        UUID taskId,
        UUID projectId,
        @NotNull LocalDate workDate,
        @NotNull @jakarta.validation.constraints.DecimalMin("0.25") @jakarta.validation.constraints.DecimalMax("24") BigDecimal hours,
        @Size(max = 500) String note) {}

    public record TimesheetReviewRequest(
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "APPROVED|REJECTED") String decision,
        @Size(max = 500) String note) {}

    public record TimesheetDecisionRequest(
        UUID entryId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "DRAFT|SUBMITTED|APPROVED|REJECTED") String status) {}

    // ===== Workload & Reports =====

    public record CapacityRequest(
        @NotNull UUID employeeId,
        @NotNull @jakarta.validation.constraints.DecimalMin("0") @jakarta.validation.constraints.DecimalMax("240") BigDecimal weeklyCapacityHours) {}

    // ===== Issue Management =====

    public record IssueCreateRequest(
        @Size(max = 60) String code,
        @NotBlank @Size(max = 30) String type,
        @Size(max = 20) String severity,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 4000) String description,
        UUID projectId,
        UUID taskId,
        UUID assigneeEmployeeId) {}

    public record IssueDecisionRequest(
        UUID issueId,
        @NotBlank @jakarta.validation.constraints.Pattern(regexp = "OPEN|IN_PROGRESS|RESOLVED|CLOSED|REOPENED") String status) {}

    public record IssueResolveRequest(
        UUID issueId,
        @Size(max = 4000) String resolutionNote) {}

    // ===== Shared =====

    public record CreatedResource(UUID id, String status) {}

    public record PmSummary(Long openProjects, Long openTasks, Long dueThisWeek, Long activeSprints,
        Long pendingTimesheets, Long openIssues, Long criticalIssues) {}
}
