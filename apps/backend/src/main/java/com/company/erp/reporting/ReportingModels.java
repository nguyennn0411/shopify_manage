package com.company.erp.reporting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportingModels {
    private ReportingModels() {}

    public record CreatedResource(UUID id, String status) {}
    public record ExecutiveSummary(String currency, long activeEmployees, long activeProjects, long overdueTasks,
        BigDecimal revenue, BigDecimal expenses, BigDecimal profit, BigDecimal openPipeline,
        BigDecimal overdueReceivables, long pendingApprovals, long lowStockItems, long objectivesAtRisk) {}
    public record TrendPoint(String period, BigDecimal revenue, BigDecimal expenses, long completedTasks) {}
    public record KeyResultRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=255) String title,
        @NotBlank @Pattern(regexp="NUMBER|PERCENT|CURRENCY|BOOLEAN") String metricType,
        @NotNull BigDecimal baselineValue,@NotNull BigDecimal targetValue,@NotNull BigDecimal currentValue,
        @Size(max=40) String unit,@NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal weight) {}
    public record ObjectiveRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=255) String title,
        @Size(max=1500) String description,@NotBlank @Pattern(regexp="COMPANY|DEPARTMENT|PROJECT") String scopeType,
        @NotNull UUID scopeId,@NotBlank @Size(max=100) String ownerSubject,@NotNull LocalDate startDate,
        @NotNull LocalDate endDate,@NotEmpty @Size(max=25) List<@Valid KeyResultRequest> keyResults) {}
    public record ProgressRequest(@NotNull BigDecimal currentValue,
        @NotBlank @Pattern(regexp="ON_TRACK|AT_RISK|OFF_TRACK|COMPLETED") String status) {}
    public record StatusRequest(@NotBlank @Pattern(regexp="ACTIVE|AT_RISK|COMPLETED|ARCHIVED") String status) {}
    public record SnapshotRequest(@NotBlank @Size(max=255) String name,
        @NotBlank @Pattern(regexp="EXECUTIVE|FINANCE|PROJECT|HR|SALES|OPERATIONS") String reportType,
        @NotNull LocalDate periodStart,@NotNull LocalDate periodEnd) {}
}
