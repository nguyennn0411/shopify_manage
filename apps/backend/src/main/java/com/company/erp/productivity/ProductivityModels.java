package com.company.erp.productivity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.UUID;

public final class ProductivityModels {
    private ProductivityModels() {}
    public record CreatedResource(UUID id,String status) {}
    public record ProductivitySummary(long unreadNotifications,long pendingApprovals,long pendingSignatures,
        long assignedTasks,long openTickets,long savedViews) {}
    public record SearchResult(String type,String id,String title,String subtitle,String path,String status) {}
    public record NotificationRequest(@Size(max=100) String targetSubject,@NotBlank @Size(max=255) String title,
        @Size(max=2000) String body,@Size(max=50) String category,
        @Pattern(regexp="INFO|SUCCESS|WARNING|CRITICAL") String severity,@Size(max=500) String actionPath,
        @Size(max=60) String sourceType,@Size(max=100) String sourceId,@Size(max=180) String dedupeKey) {}
    public record NotificationPreferenceRequest(@NotBlank @Size(max=50) String category,boolean inAppEnabled,
        boolean emailEnabled,LocalTime quietStart,LocalTime quietEnd) {}
    public record SavedViewRequest(@NotBlank @Size(max=80) String moduleKey,@NotBlank @Size(max=180) String name,
        @NotBlank String configJson,boolean shared,boolean defaultView) {}
    public record AuditQuery(@Size(max=100) String action,@Size(max=80) String resourceType,
        @Size(max=100) String actor,@Min(1) @Max(200) Integer limit) {}
}
