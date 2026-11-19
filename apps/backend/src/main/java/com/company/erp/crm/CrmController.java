package com.company.erp.crm;

import static com.company.erp.crm.CrmModels.*;

import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/crm")
public class CrmController {
    private final CrmService crm;

    public CrmController(CrmService crm) { this.crm = crm; }

    @GetMapping("/summary")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.access')")
    CrmSummary summary(@RequestParam UUID companyId, Authentication authentication) {
        return crm.summary(companyId, authentication);
    }

    @GetMapping("/customers")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.customer.read')")
    List<Map<String, Object>> customers(@RequestParam UUID companyId,
            @RequestParam(required=false) String search, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.customers(companyId, search, status, authentication);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.customer.write')")
    CreatedResource createCustomer(@RequestParam UUID companyId, @Valid @RequestBody CustomerRequest request,
            Authentication authentication) {
        return crm.createCustomer(companyId, request, authentication.getName());
    }

    @PatchMapping("/customers/{customerId}")
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#customerId,'crm.customer.write')")
    CreatedResource updateCustomer(@RequestParam UUID companyId, @PathVariable UUID customerId,
            @Valid @RequestBody CustomerRequest request, Authentication authentication) {
        return crm.updateCustomer(companyId, customerId, request, authentication.getName());
    }

    @GetMapping("/customers/{customerId}/contacts")
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#customerId,'crm.customer.read')")
    List<Map<String, Object>> contacts(@RequestParam UUID companyId, @PathVariable UUID customerId) {
        return crm.contacts(companyId, customerId);
    }

    @PostMapping("/customers/{customerId}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#customerId,'crm.customer.write')")
    CreatedResource addContact(@RequestParam UUID companyId, @PathVariable UUID customerId,
            @Valid @RequestBody ContactRequest request, Authentication authentication) {
        return crm.addContact(companyId, customerId, request, authentication.getName());
    }

    @GetMapping("/customers/{customerId}/interactions")
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#customerId,'crm.customer.read')")
    List<Map<String, Object>> interactions(@RequestParam UUID companyId, @PathVariable UUID customerId) {
        return crm.interactions(companyId, customerId);
    }

    @PostMapping("/customers/{customerId}/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#customerId,'crm.customer.write')")
    CreatedResource addInteraction(@RequestParam UUID companyId, @PathVariable UUID customerId,
            @Valid @RequestBody InteractionRequest request, Authentication authentication) {
        return crm.addInteraction(companyId, customerId, request, authentication.getName());
    }

    @GetMapping("/pipeline/stages")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.pipeline.read')")
    List<Map<String, Object>> stages(@RequestParam UUID companyId) { return crm.stages(companyId); }

    @GetMapping("/leads")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.pipeline.read')")
    List<Map<String, Object>> leads(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.leads(companyId, status, authentication);
    }

    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.pipeline.write')")
    CreatedResource createLead(@RequestParam UUID companyId, @Valid @RequestBody LeadRequest request,
            Authentication authentication) {
        return crm.createLead(companyId, request, authentication.getName());
    }

    @GetMapping("/opportunities")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.pipeline.read')")
    List<Map<String, Object>> opportunities(@RequestParam UUID companyId,
            @RequestParam(required=false) UUID stageId, @RequestParam(required=false) UUID customerId,
            Authentication authentication) {
        return crm.opportunities(companyId, stageId, customerId, authentication);
    }

    @PostMapping("/opportunities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#request.customerId(),'crm.pipeline.write')")
    CreatedResource createOpportunity(@RequestParam UUID companyId, @Valid @RequestBody OpportunityRequest request,
            Authentication authentication) {
        return crm.createOpportunity(companyId, request, authentication.getName());
    }

    @PostMapping("/opportunities/{opportunityId}/stage")
    @PreAuthorize("@crmAccess.canAccessOpportunity(authentication,#companyId,#opportunityId,'crm.pipeline.write')")
    CreatedResource moveOpportunity(@RequestParam UUID companyId, @PathVariable UUID opportunityId,
            @Valid @RequestBody OpportunityStageRequest request, Authentication authentication) {
        return crm.moveOpportunity(companyId, opportunityId, request, authentication.getName());
    }

    @GetMapping("/quotes")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.quote.read')")
    List<Map<String, Object>> quotes(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.quotes(companyId, status, authentication);
    }

    @GetMapping("/quote-templates")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.quote.read')")
    List<Map<String, Object>> quoteTemplates(@RequestParam UUID companyId) {
        return crm.quoteTemplates(companyId);
    }

    @PostMapping("/quote-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.quote.write')")
    CreatedResource createQuoteTemplate(@RequestParam UUID companyId,
            @Valid @RequestBody QuoteTemplateRequest request, Authentication authentication) {
        return crm.createQuoteTemplate(companyId, request, authentication.getName());
    }

    @GetMapping("/quotes/{quoteId}/document")
    @PreAuthorize("@crmAccess.canAccessQuote(authentication,#companyId,#quoteId,'crm.quote.read')")
    Map<String, Object> quoteDocument(@RequestParam UUID companyId, @PathVariable UUID quoteId) {
        return crm.quoteDocument(companyId, quoteId);
    }

    @GetMapping("/quotes/{quoteId}/items")
    @PreAuthorize("@crmAccess.canAccessQuote(authentication,#companyId,#quoteId,'crm.quote.read')")
    List<Map<String, Object>> quoteItems(@RequestParam UUID companyId, @PathVariable UUID quoteId) {
        return crm.quoteItems(companyId, quoteId);
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#request.customerId(),'crm.quote.write')")
    CreatedResource createQuote(@RequestParam UUID companyId, @Valid @RequestBody QuoteRequest request,
            Authentication authentication) {
        return crm.createQuote(companyId, request, authentication.getName());
    }

    @PostMapping("/quotes/{quoteId}/submit")
    @PreAuthorize("@crmAccess.canAccessQuote(authentication,#companyId,#quoteId,'crm.quote.write')")
    CreatedResource submitQuote(@RequestParam UUID companyId, @PathVariable UUID quoteId,
            Authentication authentication) {
        return crm.decideQuote(companyId, quoteId, new DecisionRequest("SUBMITTED", null), authentication.getName());
    }

    @PostMapping("/quotes/{quoteId}/decision")
    @PreAuthorize("@crmAccess.canAccessQuote(authentication,#companyId,#quoteId,'crm.quote.approve')")
    CreatedResource decideQuote(@RequestParam UUID companyId, @PathVariable UUID quoteId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return crm.decideQuote(companyId, quoteId, request, authentication.getName());
    }

    @GetMapping("/contracts")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.contract.read')")
    List<Map<String, Object>> contracts(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.contracts(companyId, status, authentication);
    }

    @PostMapping("/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#request.customerId(),'crm.contract.write')")
    CreatedResource createContract(@RequestParam UUID companyId, @Valid @RequestBody ContractRequest request,
            Authentication authentication) {
        return crm.createContract(companyId, request, authentication.getName());
    }

    @GetMapping("/contracts/{contractId}/payments")
    @PreAuthorize("@crmAccess.canAccessContract(authentication,#companyId,#contractId,'crm.contract.read')")
    List<Map<String, Object>> contractPayments(@RequestParam UUID companyId, @PathVariable UUID contractId) {
        return crm.contractPayments(companyId, contractId);
    }

    @PostMapping("/contracts/{contractId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessContract(authentication,#companyId,#contractId,'crm.contract.write')")
    CreatedResource addContractPayment(@RequestParam UUID companyId, @PathVariable UUID contractId,
            @Valid @RequestBody ContractPaymentRequest request, Authentication authentication) {
        return crm.addContractPayment(companyId, contractId, request, authentication.getName());
    }

    @PostMapping("/contracts/{contractId}/decision")
    @PreAuthorize("@crmAccess.canAccessContract(authentication,#companyId,#contractId,'crm.contract.write')")
    CreatedResource decideContract(@RequestParam UUID companyId, @PathVariable UUID contractId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return crm.decideContract(companyId, contractId, request, authentication.getName());
    }

    @GetMapping("/orders")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.order.read')")
    List<Map<String, Object>> orders(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.orders(companyId, status, authentication);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#request.customerId(),'crm.order.write')")
    CreatedResource createOrder(@RequestParam UUID companyId, @Valid @RequestBody SalesOrderRequest request,
            Authentication authentication) {
        return crm.createOrder(companyId, request, authentication.getName());
    }

    @PostMapping("/orders/{orderId}/decision")
    @PreAuthorize("@crmAccess.canAccessOrder(authentication,#companyId,#orderId,'crm.order.write')")
    CreatedResource decideOrder(@RequestParam UUID companyId, @PathVariable UUID orderId,
            @Valid @RequestBody DecisionRequest request, Authentication authentication) {
        return crm.decideOrder(companyId, orderId, request, authentication.getName());
    }

    @PostMapping("/orders/{orderId}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessOrder(authentication,#companyId,#orderId,'crm.order.write')")
    CreatedResource createReturn(@RequestParam UUID companyId, @PathVariable UUID orderId,
            @Valid @RequestBody SalesReturnRequest request, Authentication authentication) {
        return crm.createReturn(companyId, orderId, request, authentication.getName());
    }

    @GetMapping("/tickets")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.support.read')")
    List<Map<String, Object>> tickets(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            @RequestParam(required=false) UUID customerId, Authentication authentication) {
        return crm.tickets(companyId, status, customerId, authentication);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessCustomer(authentication,#companyId,#request.customerId(),'crm.support.write')")
    CreatedResource createTicket(@RequestParam UUID companyId, @Valid @RequestBody TicketRequest request,
            Authentication authentication) {
        return crm.createTicket(companyId, request, authentication.getName());
    }

    @PostMapping("/tickets/{ticketId}/decision")
    @PreAuthorize("@crmAccess.canAccessTicket(authentication,#companyId,#ticketId,'crm.support.write')")
    CreatedResource decideTicket(@RequestParam UUID companyId, @PathVariable UUID ticketId,
            @Valid @RequestBody TicketDecisionRequest request, Authentication authentication) {
        return crm.decideTicket(companyId, ticketId, request, authentication.getName());
    }

    @GetMapping("/tickets/{ticketId}/comments")
    @PreAuthorize("@crmAccess.canAccessTicket(authentication,#companyId,#ticketId,'crm.support.read')")
    List<Map<String, Object>> ticketComments(@RequestParam UUID companyId, @PathVariable UUID ticketId) {
        return crm.ticketComments(companyId, ticketId);
    }

    @PostMapping("/tickets/{ticketId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@crmAccess.canAccessTicket(authentication,#companyId,#ticketId,'crm.support.write')")
    CreatedResource addTicketComment(@RequestParam UUID companyId, @PathVariable UUID ticketId,
            @Valid @RequestBody TicketCommentRequest request, Authentication authentication) {
        return crm.addTicketComment(companyId, ticketId, request, authentication.getName());
    }

    @GetMapping("/campaigns")
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.marketing.read')")
    List<Map<String, Object>> campaigns(@RequestParam UUID companyId, @RequestParam(required=false) String status,
            Authentication authentication) {
        return crm.campaigns(companyId, status, authentication);
    }

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@companyAccess.canAccess(authentication,#companyId,'crm.marketing.write')")
    CreatedResource createCampaign(@RequestParam UUID companyId, @Valid @RequestBody CampaignRequest request,
            Authentication authentication) {
        return crm.createCampaign(companyId, request, authentication.getName());
    }
}
