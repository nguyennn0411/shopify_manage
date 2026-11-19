package com.company.erp.crm;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
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

public final class CrmModels {
    private CrmModels() {}

    public record CreatedResource(UUID id, String status) {}
    public record CrmSummary(Long activeCustomers, Long openLeads, Long openOpportunities,
        BigDecimal weightedPipeline, Long pendingQuotes, Long expiringContracts,
        Long openOrders, Long openTickets, Long breachedSla) {}

    public record CustomerRequest(
        @Size(max=60) String code,
        @NotBlank @Size(max=180) String displayName,
        @NotBlank @Pattern(regexp="PERSON|ORGANIZATION") String customerType,
        @Email @Size(max=254) String email,
        @Size(max=40) String phone,
        @Size(max=60) String taxCode,
        @Size(max=500) String website,
        @Size(max=4000) String address,
        @Size(max=80) String segment,
        @Size(max=80) String source,
        UUID ownerEmployeeId,
        @Size(max=4000) String notes) {}

    public record ContactRequest(@NotBlank @Size(max=180) String fullName,
        @Size(max=120) String jobTitle, @Email @Size(max=254) String email,
        @Size(max=40) String phone, Boolean primary, @Size(max=2000) String notes) {}

    public record InteractionRequest(@NotBlank @Size(max=40) String interactionType,
        @NotBlank @Size(max=255) String subject, @Size(max=4000) String details,
        UUID opportunityId, @NotNull LocalDateTime occurredAt) {}

    public record CampaignRequest(@Size(max=60) String code, @NotBlank @Size(max=255) String name,
        @NotBlank @Size(max=40) String campaignType, @Size(max=60) String channel,
        @NotBlank @Pattern(regexp="PLANNED|ACTIVE|PAUSED|COMPLETED|CANCELLED") String status,
        LocalDate startsOn, LocalDate endsOn, @DecimalMin("0") BigDecimal budget,
        @DecimalMin("0") BigDecimal actualCost, @Size(max=3) String currency,
        @Min(0) Integer leadsGenerated, @Min(0) Integer conversions,
        UUID ownerEmployeeId, @Size(max=4000) String objective) {}

    public record LeadRequest(@Size(max=60) String code, @NotBlank @Size(max=180) String displayName,
        @Size(max=255) String organizationName, @Email @Size(max=254) String email,
        @Size(max=40) String phone, @Size(max=80) String source,
        @Min(0) @Max(100) Integer score, @DecimalMin("0") BigDecimal estimatedValue,
        @Size(max=3) String currency, UUID ownerEmployeeId, UUID campaignId,
        @Size(max=4000) String notes) {}

    public record OpportunityRequest(@Size(max=60) String code, @NotBlank @Size(max=255) String title,
        @NotNull UUID customerId, UUID leadId, @NotNull UUID stageId,
        @NotNull @DecimalMin("0") BigDecimal value, @NotBlank @Size(max=3) String currency,
        @Min(0) @Max(100) Integer probability, LocalDate expectedCloseOn,
        UUID ownerEmployeeId, @Size(max=500) String nextStep) {}

    public record OpportunityStageRequest(@NotNull UUID stageId, @Size(max=500) String lostReason,
        @Size(max=500) String nextStep) {}

    public record LineItemRequest(@Size(max=80) String itemCode,
        @NotBlank @Size(max=500) String description,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @DecimalMin("0") @Max(100) BigDecimal discountPercent) {}

    public record QuoteTemplateRequest(@NotBlank @Size(max=180) String name,
        @NotBlank @Size(max=255) String title, @Size(max=20) String numberPrefix,
        @Size(max=4000) String defaultTerms, @Size(max=2000) String footer) {}

    public record QuoteRequest(@Size(max=80) String quoteNumber, @NotNull UUID customerId,
        UUID opportunityId, UUID templateId, @NotBlank @Size(max=3) String currency,
        @DecimalMin("0") BigDecimal discountAmount, @DecimalMin("0") BigDecimal taxAmount,
        LocalDate validUntil, @Size(max=4000) String terms,
        @NotEmpty @Valid List<LineItemRequest> items) {}

    public record DecisionRequest(@NotBlank @Size(max=30) String status, @Size(max=500) String note) {}

    public record ContractRequest(@Size(max=80) String contractNumber,
        @NotBlank @Size(max=255) String title, @NotNull UUID customerId, UUID quoteId,
        @NotNull LocalDate startsOn, LocalDate endsOn,
        @NotNull @DecimalMin("0") BigDecimal value, @NotBlank @Size(max=3) String currency,
        @Size(max=4000) String terms, @Size(max=500) String objectKey,
        @Min(0) @Max(3650) Integer renewalNoticeDays, UUID ownerEmployeeId) {}

    public record ContractPaymentRequest(@NotBlank @Size(max=180) String installmentName,
        @NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull LocalDate dueOn) {}

    public record SalesOrderRequest(@Size(max=80) String orderNumber, @NotNull UUID customerId,
        UUID quoteId, UUID contractId, @NotBlank @Size(max=3) String currency,
        @DecimalMin("0") BigDecimal discountAmount, LocalDate orderedOn,
        LocalDate expectedDeliveryOn, UUID ownerEmployeeId, @Size(max=2000) String notes,
        @NotEmpty @Valid List<LineItemRequest> items) {}

    public record SalesReturnRequest(@NotBlank @Size(max=500) String reason,
        @NotNull @DecimalMin("0") BigDecimal amount) {}

    public record TicketRequest(@NotNull UUID customerId, UUID contactId, UUID salesOrderId,
        @NotBlank @Size(max=255) String subject, @Size(max=4000) String description,
        @NotBlank @Pattern(regexp="WEB|EMAIL|PHONE|CHAT|SOCIAL") String channel,
        @NotBlank @Pattern(regexp="LOW|NORMAL|HIGH|URGENT|CRITICAL") String priority,
        UUID assigneeEmployeeId, LocalDateTime slaDueAt) {}

    public record TicketDecisionRequest(
        @NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|WAITING_CUSTOMER|RESOLVED|CLOSED|REOPENED") String status,
        @Size(max=4000) String resolution, @Min(1) @Max(5) Integer satisfactionScore) {}

    public record TicketCommentRequest(@NotBlank @Size(max=4000) String body, Boolean internalNote) {}
}
