package com.company.erp.workplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class WorkplaceModels {
    private WorkplaceModels() {}
    public record CreatedResource(UUID id,String status) {}
    public record WorkplaceSummary(long documents,long publishedArticles,long pendingApprovals,long pendingSignatures,
        long openTickets,long activeChannels,long expiringLegalRecords,long dueObligations) {}

    public record FolderRequest(UUID parentFolderId,@NotBlank @Size(max=180) String name,
        @Size(max=500) String description,@Pattern(regexp="PRIVATE|DEPARTMENT|PROJECT|COMPANY") String scopeType,
        UUID scopeId) {}
    public record DocumentRequest(UUID folderId,@NotBlank @Size(max=255) String title,@Size(max=80) String category,
        @Size(max=1000) String description,@Pattern(regexp="PRIVATE|DEPARTMENT|PROJECT|COMPANY") String scopeType,
        UUID scopeId) {}
    public record DocumentVersionRequest(@NotBlank @Size(max=255) String fileName,@NotBlank @Size(max=500) String objectKey,
        @NotBlank @Size(max=120) String mimeType,@NotNull @Min(1) Long sizeBytes,
        @NotBlank @Pattern(regexp="[0-9a-fA-F]{64}") String checksumSha256,@Size(max=500) String changeNote) {}
    public record DocumentShareRequest(@NotBlank @Size(max=100) String subject,
        @Pattern(regexp="READ|EDIT") String accessLevel) {}
    public record ScanStatusRequest(@Pattern(regexp="CLEAN|BLOCKED") String status) {}
    public record StatusRequest(@NotBlank String status,@Size(max=500) String note) {}

    public record KnowledgeSpaceRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=180) String name,
        @Size(max=500) String description,@Pattern(regexp="DEPARTMENT|PROJECT|COMPANY") String scopeType,UUID scopeId) {}
    public record KnowledgeArticleRequest(@NotNull UUID spaceId,@NotBlank @Size(max=180) String slug,
        @NotBlank @Size(max=255) String title,@Size(max=500) String summary,@NotBlank String content) {}

    public record ApproverRequest(@NotBlank @Size(max=100) String subject) {}
    public record ApprovalRequest(@NotBlank @Size(max=60) String requestType,@NotBlank @Size(max=255) String title,
        @Size(max=1000) String description,@Size(max=60) String resourceType,UUID resourceId,
        UUID departmentId,UUID projectId,@DecimalMin("0") BigDecimal amount,
        @Pattern(regexp="[A-Z]{3}") String currency,@NotEmpty @Valid List<ApproverRequest> approvers) {}
    public record CommentRequest(@NotBlank @Size(max=2000) String content) {}

    public record FormDefinitionRequest(@NotBlank @Size(max=60) String code,@NotBlank @Size(max=180) String name,
        @Size(max=500) String description,@NotBlank String schemaJson,boolean requiresApproval) {}
    public record FormSubmissionRequest(@NotBlank String payloadJson,List<@NotBlank String> approverSubjects) {}

    public record SignatureRequest(@NotNull UUID documentId,@NotBlank @Size(max=255) String title,
        @Size(max=1000) String message,LocalDateTime expiresAt,@NotEmpty List<@NotBlank String> signerSubjects) {}
    public record SignatureDecisionRequest(@Pattern(regexp="SIGNED|DECLINED") String status,
        @Size(max=500) String note) {}

    public record HelpdeskTicketRequest(@Pattern(regexp="IT|ACCESS|DEVICE|HR|ADMIN|FACILITY|OTHER") String ticketType,
        @NotBlank @Size(max=255) String subject,@NotBlank @Size(max=2000) String description,
        @Pattern(regexp="LOW|NORMAL|HIGH|URGENT") String priority,UUID departmentId) {}
    public record HelpdeskUpdateRequest(@Pattern(regexp="ASSIGNED|IN_PROGRESS|WAITING_USER|RESOLVED|CLOSED|CANCELLED") String status,
        @Size(max=100) String assignedSubject,LocalDateTime dueAt,@Min(1) Integer satisfactionScore) {}
    public record HelpdeskCommentRequest(@NotBlank @Size(max=2000) String content,boolean internalNote) {}

    public record ChannelRequest(@NotBlank @Size(max=80) String code,@NotBlank @Size(max=180) String name,
        @Pattern(regexp="COMPANY|DEPARTMENT|PROJECT") String channelType,UUID departmentId,UUID projectId,
        @Size(max=500) String description) {}
    public record MessageRequest(UUID parentMessageId,@NotBlank @Size(max=4000) String content) {}

    public record LegalRecordRequest(String recordNumber,@NotBlank @Size(max=50) String recordType,
        @NotBlank @Size(max=255) String title,@Size(max=255) String counterparty,LocalDate effectiveOn,
        LocalDate expiresOn,@Size(max=100) String ownerSubject,UUID documentId,@Size(max=2000) String notes) {}
    public record LegalObligationRequest(@NotBlank @Size(max=255) String title,@NotNull LocalDate dueOn,
        @Size(max=30) String recurrence,@Size(max=100) String ownerSubject,@Min(0) Integer reminderDays) {}
}
