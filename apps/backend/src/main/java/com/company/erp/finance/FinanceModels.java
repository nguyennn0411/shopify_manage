package com.company.erp.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceModels {
    private FinanceModels() {}

    public record CreatedResource(UUID id, String status) {}
    public record FinanceSummary(BigDecimal income, BigDecimal expenses, BigDecimal profit,
        BigDecimal receivables, BigDecimal payables, BigDecimal availableBudget,
        Long pendingTransactions, Long overdueInvoices, Long pendingAdvances) {}

    public record BudgetRequest(@Size(max=60) String code, @NotBlank @Size(max=255) String name,
        UUID departmentId, UUID projectId, UUID categoryId, @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd, @NotBlank @Size(max=3) String currency,
        @NotNull @DecimalMin("0") BigDecimal plannedAmount, @Size(max=4000) String notes) {}

    public record CashTransactionRequest(@NotBlank @Pattern(regexp="RECEIPT|PAYMENT") String transactionType,
        @NotNull UUID categoryId, UUID departmentId, UUID projectId, UUID customerId, UUID supplierId,
        @NotNull @DecimalMin("0.01") BigDecimal amount, @NotBlank @Size(max=3) String currency,
        @NotNull LocalDate occurredOn, @Size(max=30) String paymentMethod, @Size(max=120) String reference,
        @NotBlank @Size(max=500) String description, @Size(max=500) String documentObjectKey) {}

    public record DecisionRequest(@NotBlank @Size(max=30) String status, @Size(max=500) String note) {}

    public record InvoiceRequest(@Size(max=80) String invoiceNumber,
        @NotBlank @Pattern(regexp="RECEIVABLE|PAYABLE") String direction,
        UUID customerId, UUID supplierId, UUID salesOrderId, UUID contractId,
        UUID projectId, UUID departmentId, @NotNull LocalDate issueDate, @NotNull LocalDate dueDate,
        @NotNull @DecimalMin("0") BigDecimal subtotal, @DecimalMin("0") BigDecimal taxAmount,
        @NotBlank @Size(max=3) String currency, @Size(max=500) String description,
        @Size(max=500) String documentObjectKey) {}

    public record InvoicePaymentRequest(@NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull LocalDate paidOn, @Size(max=30) String paymentMethod,
        @Size(max=120) String reference, @Size(max=500) String notes) {}

    public record AdvanceRequest(@NotNull UUID employeeId, UUID departmentId, UUID projectId,
        @NotBlank @Size(max=500) String purpose, @NotNull @DecimalMin("0.01") BigDecimal requestedAmount,
        @NotBlank @Size(max=3) String currency, @NotNull LocalDate neededOn) {}

    public record AdvanceExpenseRequest(@NotNull UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount, @NotNull LocalDate incurredOn,
        @NotBlank @Size(max=500) String description, @Size(max=500) String documentObjectKey) {}

    public record AdvanceSettlementRequest(@DecimalMin("0") BigDecimal returnedAmount) {}

    public record ProjectCostRequest(@NotNull UUID projectId, @NotNull UUID categoryId,
        @NotBlank @Size(max=30) String costType, @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank @Size(max=3) String currency, @NotNull LocalDate incurredOn,
        @Size(max=40) String sourceType, UUID sourceId, @NotBlank @Size(max=500) String description) {}

    public record AccountRequest(@NotBlank @Size(max=30) String code,
        @NotBlank @Size(max=180) String name,
        @NotBlank @Pattern(regexp="ASSET|LIABILITY|EQUITY|REVENUE|EXPENSE") String accountType,
        UUID parentAccountId) {}

    public record JournalLineRequest(@NotNull UUID accountId, UUID departmentId, UUID projectId,
        @Size(max=500) String description, @DecimalMin("0") BigDecimal debit,
        @DecimalMin("0") BigDecimal credit) {}

    public record JournalRequest(@NotNull LocalDate journalDate,
        @NotBlank @Size(max=500) String description, @Size(max=120) String reference,
        @NotEmpty @Valid List<JournalLineRequest> lines) {}

    public record PeriodRequest(@Min(2000) @Max(2200) int year,
        @Min(1) @Max(12) int month, @NotBlank @Pattern(regexp="OPEN|CLOSED") String status) {}
}
