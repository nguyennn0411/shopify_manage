package com.company.erp.finance;

import static com.company.erp.finance.FinanceModels.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final FinanceService finance;
    public FinanceController(FinanceService finance) { this.finance = finance; }

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.access')")
    FinanceSummary summary(@RequestParam UUID companyId, Authentication authentication) {
        return finance.summary(companyId, authentication);
    }

    @GetMapping("/categories")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.access')")
    List<Map<String,Object>> categories(@RequestParam UUID companyId) { return finance.categories(companyId); }

    @GetMapping("/references/{kind}")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.access')")
    List<Map<String,Object>> references(@RequestParam UUID companyId, @PathVariable String kind) {
        return finance.references(companyId, kind);
    }

    @GetMapping("/budgets")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.budget.read')")
    List<Map<String,Object>> budgets(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) { return finance.budgets(companyId, status, authentication); }

    @PostMapping("/budgets") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.budget.write')")
    CreatedResource createBudget(@RequestParam UUID companyId, @Valid @RequestBody BudgetRequest request,
            Authentication authentication) { return finance.createBudget(companyId, request, authentication.getName()); }

    @PostMapping("/budgets/{budgetId}/decision")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_budgets',#budgetId,'finance.budget.write')")
    CreatedResource decideBudget(@RequestParam UUID companyId, @PathVariable UUID budgetId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return finance.decideBudget(companyId, budgetId, request, authentication.getName());
    }

    @GetMapping("/transactions")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.transaction.read')")
    List<Map<String,Object>> transactions(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) { return finance.transactions(companyId, status, authentication); }

    @PostMapping("/transactions") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.transaction.write')")
    CreatedResource createTransaction(@RequestParam UUID companyId, @Valid @RequestBody CashTransactionRequest request,
            Authentication authentication) { return finance.createTransaction(companyId, request, authentication.getName()); }

    @PostMapping("/transactions/{transactionId}/submit")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_cash_transactions',#transactionId,'finance.transaction.write')")
    CreatedResource submitTransaction(@RequestParam UUID companyId, @PathVariable UUID transactionId,
            Authentication authentication) {
        return finance.decideTransaction(companyId, transactionId, new DecisionRequest("SUBMITTED", null), authentication.getName());
    }

    @PostMapping("/transactions/{transactionId}/decision")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_cash_transactions',#transactionId,'finance.transaction.approve')")
    CreatedResource decideTransaction(@RequestParam UUID companyId, @PathVariable UUID transactionId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return finance.decideTransaction(companyId, transactionId, request, authentication.getName());
    }

    @GetMapping("/invoices")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.invoice.read')")
    List<Map<String,Object>> invoices(@RequestParam UUID companyId, @RequestParam(required=false) String direction,
            @RequestParam(required=false) String status, Authentication authentication) {
        return finance.invoices(companyId, direction, status, authentication);
    }

    @PostMapping("/invoices") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.invoice.write')")
    CreatedResource createInvoice(@RequestParam UUID companyId, @Valid @RequestBody InvoiceRequest request,
            Authentication authentication) { return finance.createInvoice(companyId, request, authentication.getName()); }

    @GetMapping("/invoices/{invoiceId}/payments")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_invoices',#invoiceId,'finance.invoice.read')")
    List<Map<String,Object>> invoicePayments(@RequestParam UUID companyId, @PathVariable UUID invoiceId) {
        return finance.invoicePayments(companyId, invoiceId);
    }

    @PostMapping("/invoices/{invoiceId}/payments") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_invoices',#invoiceId,'finance.invoice.write')")
    CreatedResource addInvoicePayment(@RequestParam UUID companyId, @PathVariable UUID invoiceId,
            @Valid @RequestBody InvoicePaymentRequest request, Authentication authentication) {
        return finance.addInvoicePayment(companyId, invoiceId, request, authentication.getName());
    }

    @GetMapping("/advances")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.advance.read')")
    List<Map<String,Object>> advances(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) { return finance.advances(companyId, status, authentication); }

    @PostMapping("/advances") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.advance.write')")
    CreatedResource createAdvance(@RequestParam UUID companyId, @Valid @RequestBody AdvanceRequest request,
            Authentication authentication) { return finance.createAdvance(companyId, request, authentication.getName()); }

    @PostMapping("/advances/{advanceId}/submit")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_advances',#advanceId,'finance.advance.write')")
    CreatedResource submitAdvance(@RequestParam UUID companyId, @PathVariable UUID advanceId,
            Authentication authentication) {
        return finance.decideAdvance(companyId, advanceId, new DecisionRequest("SUBMITTED", null), authentication.getName());
    }

    @PostMapping("/advances/{advanceId}/decision")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_advances',#advanceId,'finance.advance.approve')")
    CreatedResource decideAdvance(@RequestParam UUID companyId, @PathVariable UUID advanceId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return finance.decideAdvance(companyId, advanceId, request, authentication.getName());
    }

    @GetMapping("/advances/{advanceId}/expenses")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_advances',#advanceId,'finance.advance.read')")
    List<Map<String,Object>> advanceExpenses(@RequestParam UUID companyId, @PathVariable UUID advanceId) {
        return finance.advanceExpenses(companyId, advanceId);
    }

    @PostMapping("/advances/{advanceId}/expenses") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_advances',#advanceId,'finance.advance.write')")
    CreatedResource addAdvanceExpense(@RequestParam UUID companyId, @PathVariable UUID advanceId,
            @Valid @RequestBody AdvanceExpenseRequest request, Authentication authentication) {
        return finance.addAdvanceExpense(companyId, advanceId, request, authentication.getName());
    }

    @PostMapping("/advances/{advanceId}/settle")
    @PreAuthorize("@financeAccess.canAccessRecord(authentication,#companyId,'finance_advances',#advanceId,'finance.advance.approve')")
    CreatedResource settleAdvance(@RequestParam UUID companyId, @PathVariable UUID advanceId,
            @Valid @RequestBody AdvanceSettlementRequest request, Authentication authentication) {
        return finance.settleAdvance(companyId, advanceId, request, authentication.getName());
    }

    @GetMapping("/project-costs")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.project-cost.read')")
    List<Map<String,Object>> projectCosts(@RequestParam UUID companyId, @RequestParam(required=false) UUID projectId,
            Authentication authentication) {
        return finance.projectCosts(companyId, projectId, authentication);
    }

    @PostMapping("/project-costs") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@financeAccess.canAccessProject(authentication,#companyId,#request.projectId(),'finance.project-cost.write')")
    CreatedResource createProjectCost(@RequestParam UUID companyId, @Valid @RequestBody ProjectCostRequest request,
            Authentication authentication) { return finance.createProjectCost(companyId, request, authentication.getName()); }

    @GetMapping("/accounts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.read')")
    List<Map<String,Object>> accounts(@RequestParam UUID companyId) { return finance.accounts(companyId); }

    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.write')")
    CreatedResource createAccount(@RequestParam UUID companyId, @Valid @RequestBody AccountRequest request,
            Authentication authentication) { return finance.createAccount(companyId, request, authentication.getName()); }

    @GetMapping("/journals")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.read')")
    List<Map<String,Object>> journals(@RequestParam UUID companyId, @RequestParam(required=false) String status) {
        return finance.journals(companyId, status);
    }

    @PostMapping("/journals") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.write')")
    CreatedResource createJournal(@RequestParam UUID companyId, @Valid @RequestBody JournalRequest request,
            Authentication authentication) { return finance.createJournal(companyId, request, authentication.getName()); }

    @GetMapping("/journals/{journalId}/lines")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.read')")
    List<Map<String,Object>> journalLines(@RequestParam UUID companyId, @PathVariable UUID journalId) {
        return finance.journalLines(companyId, journalId);
    }

    @PostMapping("/journals/{journalId}/post")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.write')")
    CreatedResource postJournal(@RequestParam UUID companyId, @PathVariable UUID journalId,
            Authentication authentication) { return finance.postJournal(companyId, journalId, authentication.getName()); }

    @GetMapping("/periods")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.read')")
    List<Map<String,Object>> periods(@RequestParam UUID companyId) { return finance.periods(companyId); }

    @PostMapping("/periods")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.period.close')")
    CreatedResource setPeriod(@RequestParam UUID companyId, @Valid @RequestBody PeriodRequest request,
            Authentication authentication) { return finance.setPeriod(companyId, request, authentication.getName()); }

    @GetMapping("/reports/monthly")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.report.read')")
    List<Map<String,Object>> monthlyReport(@RequestParam UUID companyId, @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to) { return finance.monthlyReport(companyId, from, to); }

    @GetMapping("/reports/ledger")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'finance.accounting.read')")
    List<Map<String,Object>> generalLedger(@RequestParam UUID companyId, @RequestParam(required=false) LocalDate from,
            @RequestParam(required=false) LocalDate to) { return finance.generalLedger(companyId, from, to); }
}
