package com.company.erp.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AutomationModels {
    private AutomationModels() {}
    public record CreatedResource(UUID id,String status) {}
    public record AutomationSummary(long activeWorkflows,long runsToday,long failedRuns,long activeConnections,long pendingDeliveries) {}
    public record ConnectionRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=180) String name,
        @NotBlank @Pattern(regexp="EMAIL|CALENDAR|ACCOUNTING|CRM|HR|PAYMENT|CUSTOM") String connectorType,
        @Size(max=500) String baseUrl,@Size(max=500) String credentialReference,@NotBlank String configJson) {}
    public record WebhookEndpointRequest(UUID connectionId,@NotBlank @Size(max=60) String code,
        @NotBlank @Size(max=180) String name,@NotBlank @Size(max=100) String eventType,
        @NotBlank @Size(max=1000) String targetUrl,@Min(1) @Max(30) Integer timeoutSeconds,
        @Min(1) @Max(10) Integer maxAttempts) {}
    public record WorkflowStepRequest(@NotBlank @Size(max=180) String name,
        @NotBlank @Pattern(regexp="NOTIFICATION|WEBHOOK|APPROVAL") String stepType,@NotBlank String configJson,
        boolean continueOnError) {}
    public record WorkflowRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=180) String name,
        @Size(max=1000) String description,@NotBlank @Pattern(regexp="MANUAL|SCHEDULE|EVENT") String triggerType,
        @NotBlank String triggerConfigJson,@NotEmpty List<@Valid WorkflowStepRequest> steps) {}
    public record StatusRequest(@NotBlank @Pattern(regexp="ACTIVE|PAUSED|ARCHIVED|DISABLED") String status) {}
    public record RunRequest(String inputJson) {}
}
