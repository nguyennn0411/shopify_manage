package com.company.erp.crm;

import static com.company.erp.crm.CrmModels.*;

import com.company.erp.audit.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CrmService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;
    private final CrmAccess access;

    public CrmService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit, CrmAccess access) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.audit = audit;
        this.access = access;
    }

    public CrmSummary summary(UUID companyId, Authentication authentication) {
        List<Map<String, Object>> customers = access.canAccess(authentication, companyId, "crm.customer.read")
            ? customers(companyId, null, null, authentication) : List.of();
        List<Map<String, Object>> leads = access.canAccess(authentication, companyId, "crm.pipeline.read")
            ? leads(companyId, null, authentication) : List.of();
        List<Map<String, Object>> opportunities = access.canAccess(authentication, companyId, "crm.pipeline.read")
            ? opportunities(companyId, null, null, authentication) : List.of();
        List<Map<String, Object>> quotes = access.canAccess(authentication, companyId, "crm.quote.read")
            ? quotes(companyId, null, authentication) : List.of();
        List<Map<String, Object>> contracts = access.canAccess(authentication, companyId, "crm.contract.read")
            ? contracts(companyId, null, authentication) : List.of();
        List<Map<String, Object>> orders = access.canAccess(authentication, companyId, "crm.order.read")
            ? orders(companyId, null, authentication) : List.of();
        List<Map<String, Object>> tickets = access.canAccess(authentication, companyId, "crm.support.read")
            ? tickets(companyId, null, null, authentication) : List.of();
        BigDecimal weighted = opportunities.stream()
            .filter(row -> !bool(row.get("is_won")) && !bool(row.get("is_lost")))
            .map(row -> decimal(row.get("value")).multiply(decimal(row.get("probability")))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        return new CrmSummary(
            (long) customers.stream().filter(row -> "ACTIVE".equals(text(row.get("status")))).count(),
            (long) leads.stream().filter(row -> !Set.of("CONVERTED", "DISQUALIFIED").contains(text(row.get("status")))).count(),
            (long) opportunities.stream().filter(row -> !bool(row.get("is_won")) && !bool(row.get("is_lost"))).count(),
            weighted,
            (long) quotes.stream().filter(row -> Set.of("DRAFT", "SUBMITTED").contains(text(row.get("status")))).count(),
            (long) contracts.stream().filter(row -> "ACTIVE".equals(text(row.get("status")))
                && date(row.get("ends_on")) != null && !date(row.get("ends_on")).isAfter(today.plusDays(30))).count(),
            (long) orders.stream().filter(row -> !Set.of("DELIVERED", "CANCELLED", "RETURNED").contains(text(row.get("status")))).count(),
            (long) tickets.stream().filter(row -> !Set.of("RESOLVED", "CLOSED").contains(text(row.get("status")))).count(),
            (long) tickets.stream().filter(row -> !Set.of("RESOLVED", "CLOSED").contains(text(row.get("status")))
                && dateTime(row.get("sla_due_at")) != null && dateTime(row.get("sla_due_at")).isBefore(now)).count());
    }

    // Customers

    public List<Map<String, Object>> customers(UUID companyId, String search, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        StringBuilder filter = new StringBuilder(" where c.company_id=:companyId");
        if (search != null && !search.isBlank()) {
            filter.append(" and (lower(coalesce(c.display_name,'')) like :search or lower(coalesce(c.customer_code,'')) like :search or lower(coalesce(c.email,'')) like :search)");
            params.put("search", "%" + search.trim().toLowerCase() + "%");
        }
        if (status != null && !status.isBlank()) { filter.append(" and c.status=:status"); params.put("status", status); }
        return rows("""
            select c.id,c.customer_code,c.customer_type,c.display_name,c.email,c.phone,c.tax_code,c.website,c.address,
                   c.segment,c.source,c.status,c.owner_employee_id,e.full_name owner_name,c.notes,c.created_at,c.updated_at,
                   (select count(*) from crm_contacts ct where ct.company_id=c.company_id and ct.customer_id=c.id) contact_count,
                   (select count(*) from crm_opportunities o where o.company_id=c.company_id and o.customer_id=c.id) opportunity_count
            from customers c left join employees e on e.id=c.owner_employee_id
            """ + filter + " order by c.updated_at desc,c.display_name", params).stream()
            .filter(row -> access.canAccessCustomer(authentication, companyId, id(row.get("id")), "crm.customer.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createCustomer(UUID companyId, CustomerRequest request, String actor) {
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        UUID id = UUID.randomUUID();
        String code = blank(request.code()) == null ? nextCode("customers", "customer_code", "CUS", companyId) : request.code().trim();
        jdbc.sql("""
            insert into customers(id,company_id,customer_code,customer_type,display_name,email,phone,tax_code,website,address,
              segment,source,status,owner_employee_id,notes,created_by_subject)
            values(:id,:companyId,:code,:type,:name,:email,:phone,:taxCode,:website,:address,:segment,:source,'ACTIVE',:owner,:notes,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("type", request.customerType()).param("name", request.displayName()).param("email", blank(request.email()))
            .param("phone", blank(request.phone())).param("taxCode", blank(request.taxCode())).param("website", blank(request.website()))
            .param("address", blank(request.address())).param("segment", blank(request.segment())).param("source", blank(request.source()))
            .param("owner", uuid(ownerId)).param("notes", blank(request.notes())).param("actor", actor).update();
        audit.write(companyId, actor, "CRM_CUSTOMER_CREATED", "CRM_CUSTOMER", id.toString(), "{\"code\":\"" + safe(code) + "\"}");
        return new CreatedResource(id, "ACTIVE");
    }

    @Transactional
    public CreatedResource updateCustomer(UUID companyId, UUID customerId, CustomerRequest request, String actor) {
        ensure("customers", companyId, customerId, "Customer");
        ensureEmployee(companyId, request.ownerEmployeeId());
        jdbc.sql("""
            update customers set display_name=:name,customer_type=:type,email=:email,phone=:phone,tax_code=:taxCode,
              website=:website,address=:address,segment=:segment,source=:source,owner_employee_id=:owner,notes=:notes,
              updated_at=current_timestamp where company_id=:companyId and id=:id
            """).param("name", request.displayName()).param("type", request.customerType()).param("email", blank(request.email()))
            .param("phone", blank(request.phone())).param("taxCode", blank(request.taxCode())).param("website", blank(request.website()))
            .param("address", blank(request.address())).param("segment", blank(request.segment())).param("source", blank(request.source()))
            .param("owner", uuid(request.ownerEmployeeId())).param("notes", blank(request.notes()))
            .param("companyId", companyId.toString()).param("id", customerId.toString()).update();
        audit.write(companyId, actor, "CRM_CUSTOMER_UPDATED", "CRM_CUSTOMER", customerId.toString(), "{}");
        return new CreatedResource(customerId, "UPDATED");
    }

    public List<Map<String, Object>> contacts(UUID companyId, UUID customerId) {
        return rows("select id,full_name,job_title,email,phone,is_primary,notes,created_at from crm_contacts where company_id=:companyId and customer_id=:customerId order by is_primary desc,full_name",
            params(companyId, "customerId", customerId));
    }

    @Transactional
    public CreatedResource addContact(UUID companyId, UUID customerId, ContactRequest request, String actor) {
        ensure("customers", companyId, customerId, "Customer");
        UUID id = UUID.randomUUID();
        if (Boolean.TRUE.equals(request.primary())) jdbc.sql("update crm_contacts set is_primary=false where company_id=:companyId and customer_id=:customerId")
            .param("companyId", companyId.toString()).param("customerId", customerId.toString()).update();
        jdbc.sql("""
            insert into crm_contacts(id,company_id,customer_id,full_name,job_title,email,phone,is_primary,notes)
            values(:id,:companyId,:customerId,:name,:title,:email,:phone,:primary,:notes)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("customerId", customerId.toString())
            .param("name", request.fullName()).param("title", blank(request.jobTitle())).param("email", blank(request.email()))
            .param("phone", blank(request.phone())).param("primary", Boolean.TRUE.equals(request.primary()))
            .param("notes", blank(request.notes())).update();
        audit.write(companyId, actor, "CRM_CONTACT_CREATED", "CRM_CONTACT", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    public List<Map<String, Object>> interactions(UUID companyId, UUID customerId) {
        return rows("select id,opportunity_id,interaction_type,subject,details,occurred_at,created_by_subject,created_at from crm_interactions where company_id=:companyId and customer_id=:customerId order by occurred_at desc",
            params(companyId, "customerId", customerId));
    }

    @Transactional
    public CreatedResource addInteraction(UUID companyId, UUID customerId, InteractionRequest request, String actor) {
        ensure("customers", companyId, customerId, "Customer");
        if (request.opportunityId() != null) ensure("crm_opportunities", companyId, request.opportunityId(), "Opportunity");
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into crm_interactions(id,company_id,customer_id,opportunity_id,interaction_type,subject,details,occurred_at,created_by_subject)
            values(:id,:companyId,:customerId,:opportunityId,:type,:subject,:details,:occurredAt,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("customerId", customerId.toString())
            .param("opportunityId", uuid(request.opportunityId())).param("type", request.interactionType())
            .param("subject", request.subject()).param("details", blank(request.details())).param("occurredAt", request.occurredAt())
            .param("actor", actor).update();
        audit.write(companyId, actor, "CRM_INTERACTION_CREATED", "CRM_INTERACTION", id.toString(), "{}");
        return new CreatedResource(id, "RECORDED");
    }

    // Pipeline

    public List<Map<String, Object>> stages(UUID companyId) {
        return rows("select id,code,name,probability,sort_order,is_won,is_lost from crm_pipeline_stages where company_id=:companyId and active=true order by sort_order",
            params(companyId));
    }

    public List<Map<String, Object>> leads(UUID companyId, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        String filter = " where l.company_id=:companyId";
        if (status != null && !status.isBlank()) { filter += " and l.status=:status"; params.put("status", status); }
        return rows("""
            select l.id,l.code,l.display_name,l.organization_name,l.email,l.phone,l.source,l.status,l.score,
                   l.estimated_value,l.currency,l.owner_employee_id,e.full_name owner_name,l.campaign_id,c.name campaign_name,
                   l.customer_id,l.notes,l.created_at,l.updated_at
            from crm_leads l left join employees e on e.id=l.owner_employee_id left join crm_campaigns c on c.id=l.campaign_id
            """ + filter + " order by l.updated_at desc", params).stream()
            .filter(row -> access.canAccessLead(authentication, companyId, id(row.get("id")), "crm.pipeline.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createLead(UUID companyId, LeadRequest request, String actor) {
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        if (request.campaignId() != null) ensure("crm_campaigns", companyId, request.campaignId(), "Campaign");
        UUID id = UUID.randomUUID();
        String code = blank(request.code()) == null ? nextCode("crm_leads", "code", "LEAD", companyId) : request.code().trim();
        jdbc.sql("""
            insert into crm_leads(id,company_id,code,display_name,organization_name,email,phone,source,status,score,
              estimated_value,currency,owner_employee_id,campaign_id,notes)
            values(:id,:companyId,:code,:name,:organization,:email,:phone,:source,'NEW',:score,:value,:currency,:owner,:campaign,:notes)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("name", request.displayName()).param("organization", blank(request.organizationName()))
            .param("email", blank(request.email())).param("phone", blank(request.phone())).param("source", blank(request.source()))
            .param("score", request.score() == null ? 0 : request.score()).param("value", request.estimatedValue())
            .param("currency", blank(request.currency())).param("owner", uuid(ownerId))
            .param("campaign", uuid(request.campaignId())).param("notes", blank(request.notes())).update();
        audit.write(companyId, actor, "CRM_LEAD_CREATED", "CRM_LEAD", id.toString(), "{}");
        return new CreatedResource(id, "NEW");
    }

    public List<Map<String, Object>> opportunities(UUID companyId, UUID stageId, UUID customerId, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        StringBuilder filter = new StringBuilder(" where o.company_id=:companyId");
        if (stageId != null) { filter.append(" and o.stage_id=:stageId"); params.put("stageId", stageId.toString()); }
        if (customerId != null) { filter.append(" and o.customer_id=:customerId"); params.put("customerId", customerId.toString()); }
        return rows("""
            select o.id,o.code,o.title,o.customer_id,c.display_name customer_name,o.lead_id,o.stage_id,s.code stage_code,
                   s.name stage_name,s.is_won,s.is_lost,o.value,o.currency,o.probability,o.expected_close_on,
                   o.owner_employee_id,e.full_name owner_name,o.next_step,o.lost_reason,o.created_at,o.updated_at
            from crm_opportunities o join customers c on c.id=o.customer_id join crm_pipeline_stages s on s.id=o.stage_id
            left join employees e on e.id=o.owner_employee_id
            """ + filter + " order by s.sort_order,o.expected_close_on nulls last,o.updated_at desc", params).stream()
            .filter(row -> access.canAccessOpportunity(authentication, companyId, id(row.get("id")), "crm.pipeline.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createOpportunity(UUID companyId, OpportunityRequest request, String actor) {
        ensure("customers", companyId, request.customerId(), "Customer");
        ensure("crm_pipeline_stages", companyId, request.stageId(), "Pipeline stage");
        if (request.leadId() != null) ensure("crm_leads", companyId, request.leadId(), "Lead");
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        UUID id = UUID.randomUUID();
        String code = blank(request.code()) == null ? nextCode("crm_opportunities", "code", "OPP", companyId) : request.code().trim();
        int probability = request.probability() == null ? stageProbability(companyId, request.stageId()) : request.probability();
        jdbc.sql("""
            insert into crm_opportunities(id,company_id,code,title,customer_id,lead_id,stage_id,value,currency,probability,
              expected_close_on,owner_employee_id,next_step)
            values(:id,:companyId,:code,:title,:customer,:lead,:stage,:value,:currency,:probability,:closeOn,:owner,:nextStep)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("title", request.title()).param("customer", request.customerId().toString()).param("lead", uuid(request.leadId()))
            .param("stage", request.stageId().toString()).param("value", request.value()).param("currency", request.currency())
            .param("probability", probability).param("closeOn", request.expectedCloseOn()).param("owner", uuid(ownerId))
            .param("nextStep", blank(request.nextStep())).update();
        if (request.leadId() != null) jdbc.sql("update crm_leads set status='CONVERTED',customer_id=:customer,updated_at=current_timestamp where company_id=:companyId and id=:lead")
            .param("customer", request.customerId().toString()).param("companyId", companyId.toString()).param("lead", request.leadId().toString()).update();
        audit.write(companyId, actor, "CRM_OPPORTUNITY_CREATED", "CRM_OPPORTUNITY", id.toString(), "{}");
        return new CreatedResource(id, "OPEN");
    }

    @Transactional
    public CreatedResource moveOpportunity(UUID companyId, UUID opportunityId, OpportunityStageRequest request, String actor) {
        ensure("crm_opportunities", companyId, opportunityId, "Opportunity");
        ensure("crm_pipeline_stages", companyId, request.stageId(), "Pipeline stage");
        int probability = stageProbability(companyId, request.stageId());
        jdbc.sql("""
            update crm_opportunities set stage_id=:stage,probability=:probability,lost_reason=:lostReason,
              next_step=coalesce(:nextStep,next_step),updated_at=current_timestamp where company_id=:companyId and id=:id
            """).param("stage", request.stageId().toString()).param("probability", probability)
            .param("lostReason", blank(request.lostReason())).param("nextStep", blank(request.nextStep()))
            .param("companyId", companyId.toString()).param("id", opportunityId.toString()).update();
        audit.write(companyId, actor, "CRM_OPPORTUNITY_STAGE_CHANGED", "CRM_OPPORTUNITY", opportunityId.toString(),
            "{\"stageId\":\"" + request.stageId() + "\"}");
        return new CreatedResource(opportunityId, "MOVED");
    }

    // Quotes

    public List<Map<String, Object>> quoteTemplates(UUID companyId) {
        return rows("select id,name,title,number_prefix,default_terms,footer,active,created_at from crm_quote_templates where company_id=:companyId and active=true order by name",
            params(companyId));
    }

    @Transactional
    public CreatedResource createQuoteTemplate(UUID companyId, QuoteTemplateRequest request, String actor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
            insert into crm_quote_templates(id,company_id,name,title,number_prefix,default_terms,footer)
            values(:id,:companyId,:name,:title,:prefix,:terms,:footer)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("name", request.name())
            .param("title", request.title()).param("prefix", blank(request.numberPrefix()) == null ? "QT" : request.numberPrefix().trim())
            .param("terms", blank(request.defaultTerms())).param("footer", blank(request.footer())).update();
        audit.write(companyId, actor, "CRM_QUOTE_TEMPLATE_CREATED", "CRM_QUOTE_TEMPLATE", id.toString(), "{}");
        return new CreatedResource(id, "ACTIVE");
    }

    public List<Map<String, Object>> quotes(UUID companyId, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        String filter = " where q.company_id=:companyId";
        if (status != null && !status.isBlank()) { filter += " and q.status=:status"; params.put("status", status); }
        return rows("""
            select q.id,q.quote_number,q.version,q.customer_id,c.display_name customer_name,q.opportunity_id,
                   q.quote_template_id,qt.name template_name,qt.title template_title,qt.footer template_footer,q.status,
                   q.currency,q.subtotal,q.discount_amount,q.tax_amount,q.total,q.valid_until,q.terms,
                   q.created_by_subject,q.approved_by_subject,q.approved_at,q.created_at,q.updated_at
            from crm_quotes q join customers c on c.id=q.customer_id left join crm_quote_templates qt on qt.id=q.quote_template_id
            """ + filter + " order by q.created_at desc", params).stream()
            .filter(row -> access.canAccessQuote(authentication, companyId, id(row.get("id")), "crm.quote.read"))
            .toList();
    }

    public List<Map<String, Object>> quoteItems(UUID companyId, UUID quoteId) {
        return rows("select id,item_code,description,quantity,unit_price,discount_percent,line_total from crm_quote_items where company_id=:companyId and quote_id=:quoteId order by id",
            params(companyId, "quoteId", quoteId));
    }

    public Map<String, Object> quoteDocument(UUID companyId, UUID quoteId) {
        List<Map<String, Object>> result = rows("""
            select q.id,q.quote_number,q.version,q.status,q.currency,q.subtotal,q.discount_amount,q.tax_amount,q.total,
                   q.valid_until,q.terms,q.created_at,c.customer_code,c.display_name customer_name,c.email customer_email,
                   c.phone customer_phone,c.tax_code,c.address customer_address,co.name company_name,co.legal_name,
                   co.tax_code company_tax_code,co.email company_email,co.phone company_phone,co.address company_address,
                   qt.title template_title,qt.footer template_footer
            from crm_quotes q join customers c on c.id=q.customer_id join companies co on co.id=q.company_id
            left join crm_quote_templates qt on qt.id=q.quote_template_id
            where q.company_id=:companyId and q.id=:quoteId
            """, params(companyId, "quoteId", quoteId));
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found");
        Map<String, Object> document = new LinkedHashMap<>(result.getFirst());
        document.put("items", quoteItems(companyId, quoteId));
        return document;
    }

    @Transactional
    public CreatedResource createQuote(UUID companyId, QuoteRequest request, String actor) {
        ensure("customers", companyId, request.customerId(), "Customer");
        if (request.opportunityId() != null) ensure("crm_opportunities", companyId, request.opportunityId(), "Opportunity");
        if (request.templateId() != null) ensure("crm_quote_templates", companyId, request.templateId(), "Quote template");
        UUID id = UUID.randomUUID();
        String prefix = request.templateId() == null ? "QT" : jdbc.sql("select number_prefix from crm_quote_templates where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", request.templateId().toString()).query(String.class).single().trim();
        String number = blank(request.quoteNumber()) == null ? nextCode("crm_quotes", "quote_number", prefix, companyId) : request.quoteNumber().trim();
        int version = jdbc.sql("select coalesce(max(version),0)+1 from crm_quotes where company_id=:companyId and quote_number=:number")
            .param("companyId", companyId.toString()).param("number", number).query(Integer.class).single();
        BigDecimal subtotal = request.items().stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = zero(request.discountAmount());
        BigDecimal tax = zero(request.taxAmount());
        BigDecimal total = subtotal.subtract(discount).add(tax);
        if (total.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quote total cannot be negative");
        jdbc.sql("""
            insert into crm_quotes(id,company_id,quote_number,version,customer_id,opportunity_id,quote_template_id,status,currency,
              subtotal,discount_amount,tax_amount,total,valid_until,terms,created_by_subject)
            values(:id,:companyId,:number,:version,:customer,:opportunity,:template,'DRAFT',:currency,:subtotal,:discount,:tax,:total,:validUntil,:terms,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("number", number)
            .param("version", version).param("customer", request.customerId().toString()).param("opportunity", uuid(request.opportunityId()))
            .param("template", uuid(request.templateId()))
            .param("currency", request.currency()).param("subtotal", subtotal).param("discount", discount).param("tax", tax)
            .param("total", total).param("validUntil", request.validUntil()).param("terms", quoteTerms(companyId, request)).param("actor", actor).update();
        for (LineItemRequest item : request.items()) insertLineItem("crm_quote_items", "quote_id", id, companyId, item);
        audit.write(companyId, actor, "CRM_QUOTE_CREATED", "CRM_QUOTE", id.toString(), "{\"total\":" + total + "}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource decideQuote(UUID companyId, UUID quoteId, DecisionRequest request, String actor) {
        ensureStatus(request.status(), Set.of("DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "EXPIRED"));
        ensure("crm_quotes", companyId, quoteId, "Quote");
        jdbc.sql("""
            update crm_quotes set status=:status,approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,
              approved_at=case when :status='APPROVED' then current_timestamp else approved_at end,updated_at=current_timestamp
            where company_id=:companyId and id=:id
            """).param("status", request.status()).param("actor", actor).param("companyId", companyId.toString())
            .param("id", quoteId.toString()).update();
        audit.write(companyId, actor, "CRM_QUOTE_STATUS_CHANGED", "CRM_QUOTE", quoteId.toString(),
            "{\"status\":\"" + request.status() + "\",\"note\":\"" + safe(request.note()) + "\"}");
        return new CreatedResource(quoteId, request.status());
    }

    // Contracts

    public List<Map<String, Object>> contracts(UUID companyId, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        String filter = " where ct.company_id=:companyId";
        if (status != null && !status.isBlank()) { filter += " and ct.status=:status"; params.put("status", status); }
        return rows("""
            select ct.id,ct.contract_number,ct.title,ct.customer_id,c.display_name customer_name,ct.quote_id,ct.status,
                   ct.starts_on,ct.ends_on,ct.value,ct.currency,ct.terms,ct.private_object_key,ct.renewal_notice_days,
                   ct.owner_employee_id,e.full_name owner_name,ct.signed_at,ct.created_at,ct.updated_at,
                   (select coalesce(sum(p.amount),0) from crm_contract_payments p where p.contract_id=ct.id and p.status='PAID') paid_amount
            from crm_contracts ct join customers c on c.id=ct.customer_id left join employees e on e.id=ct.owner_employee_id
            """ + filter + " order by ct.ends_on nulls last,ct.created_at desc", params).stream()
            .filter(row -> access.canAccessContract(authentication, companyId, id(row.get("id")), "crm.contract.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createContract(UUID companyId, ContractRequest request, String actor) {
        ensure("customers", companyId, request.customerId(), "Customer");
        if (request.quoteId() != null) ensure("crm_quotes", companyId, request.quoteId(), "Quote");
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        if (request.endsOn() != null && request.endsOn().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contract end date must be on or after start date");
        UUID id = UUID.randomUUID();
        String number = blank(request.contractNumber()) == null ? nextCode("crm_contracts", "contract_number", "CTR", companyId) : request.contractNumber().trim();
        jdbc.sql("""
            insert into crm_contracts(id,company_id,contract_number,title,customer_id,quote_id,status,starts_on,ends_on,
              value,currency,terms,private_object_key,renewal_notice_days,owner_employee_id)
            values(:id,:companyId,:number,:title,:customer,:quote,'DRAFT',:startsOn,:endsOn,:value,:currency,:terms,:objectKey,:notice,:owner)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("number", number)
            .param("title", request.title()).param("customer", request.customerId().toString()).param("quote", uuid(request.quoteId()))
            .param("startsOn", request.startsOn()).param("endsOn", request.endsOn()).param("value", request.value())
            .param("currency", request.currency()).param("terms", blank(request.terms())).param("objectKey", blank(request.objectKey()))
            .param("notice", request.renewalNoticeDays() == null ? 30 : request.renewalNoticeDays())
            .param("owner", uuid(ownerId)).update();
        audit.write(companyId, actor, "CRM_CONTRACT_CREATED", "CRM_CONTRACT", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    public List<Map<String, Object>> contractPayments(UUID companyId, UUID contractId) {
        return rows("select id,installment_name,amount,due_on,status,paid_at,reference from crm_contract_payments where company_id=:companyId and contract_id=:contractId order by due_on",
            params(companyId, "contractId", contractId));
    }

    @Transactional
    public CreatedResource addContractPayment(UUID companyId, UUID contractId, ContractPaymentRequest request, String actor) {
        ensure("crm_contracts", companyId, contractId, "Contract");
        BigDecimal contractValue = jdbc.sql("select value from crm_contracts where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", contractId.toString()).query(BigDecimal.class).single();
        BigDecimal scheduled = jdbc.sql("select coalesce(sum(amount),0) from crm_contract_payments where company_id=:companyId and contract_id=:id")
            .param("companyId", companyId.toString()).param("id", contractId.toString()).query(BigDecimal.class).single();
        if (scheduled.add(request.amount()).compareTo(contractValue) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment schedule exceeds contract value");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into crm_contract_payments(id,company_id,contract_id,installment_name,amount,due_on) values(:id,:companyId,:contractId,:name,:amount,:dueOn)")
            .param("id", id.toString()).param("companyId", companyId.toString()).param("contractId", contractId.toString())
            .param("name", request.installmentName()).param("amount", request.amount()).param("dueOn", request.dueOn()).update();
        audit.write(companyId, actor, "CRM_CONTRACT_PAYMENT_SCHEDULED", "CRM_CONTRACT_PAYMENT", id.toString(), "{}");
        return new CreatedResource(id, "PENDING");
    }

    @Transactional
    public CreatedResource decideContract(UUID companyId, UUID contractId, DecisionRequest request, String actor) {
        ensureStatus(request.status(), Set.of("DRAFT", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED"));
        ensure("crm_contracts", companyId, contractId, "Contract");
        jdbc.sql("update crm_contracts set status=:status,signed_at=case when :status='ACTIVE' and signed_at is null then current_timestamp else signed_at end,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("status", request.status()).param("companyId", companyId.toString()).param("id", contractId.toString()).update();
        audit.write(companyId, actor, "CRM_CONTRACT_STATUS_CHANGED", "CRM_CONTRACT", contractId.toString(), "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(contractId, request.status());
    }

    // Sales orders

    public List<Map<String, Object>> orders(UUID companyId, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        String filter = " where o.company_id=:companyId";
        if (status != null && !status.isBlank()) { filter += " and o.status=:status"; params.put("status", status); }
        return rows("""
            select o.id,o.order_number,o.customer_id,c.display_name customer_name,o.quote_id,o.contract_id,o.status,
                   o.currency,o.subtotal,o.discount_amount,o.total,o.ordered_on,o.expected_delivery_on,
                   o.owner_employee_id,e.full_name owner_name,o.notes,o.created_at,o.updated_at
            from crm_sales_orders o join customers c on c.id=o.customer_id left join employees e on e.id=o.owner_employee_id
            """ + filter + " order by o.created_at desc", params).stream()
            .filter(row -> access.canAccessOrder(authentication, companyId, id(row.get("id")), "crm.order.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createOrder(UUID companyId, SalesOrderRequest request, String actor) {
        ensure("customers", companyId, request.customerId(), "Customer");
        if (request.quoteId() != null) ensure("crm_quotes", companyId, request.quoteId(), "Quote");
        if (request.contractId() != null) ensure("crm_contracts", companyId, request.contractId(), "Contract");
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        UUID id = UUID.randomUUID();
        String number = blank(request.orderNumber()) == null ? nextCode("crm_sales_orders", "order_number", "SO", companyId) : request.orderNumber().trim();
        BigDecimal subtotal = request.items().stream().map(this::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = zero(request.discountAmount());
        BigDecimal total = subtotal.subtract(discount);
        if (total.signum() < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order total cannot be negative");
        jdbc.sql("""
            insert into crm_sales_orders(id,company_id,order_number,customer_id,quote_id,contract_id,status,currency,
              subtotal,discount_amount,total,ordered_on,expected_delivery_on,owner_employee_id,notes)
            values(:id,:companyId,:number,:customer,:quote,:contract,'DRAFT',:currency,:subtotal,:discount,:total,:orderedOn,:deliveryOn,:owner,:notes)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("number", number)
            .param("customer", request.customerId().toString()).param("quote", uuid(request.quoteId()))
            .param("contract", uuid(request.contractId())).param("currency", request.currency()).param("subtotal", subtotal)
            .param("discount", discount).param("total", total).param("orderedOn", request.orderedOn())
            .param("deliveryOn", request.expectedDeliveryOn()).param("owner", uuid(ownerId))
            .param("notes", blank(request.notes())).update();
        for (LineItemRequest item : request.items()) insertLineItem("crm_sales_order_items", "sales_order_id", id, companyId, item);
        audit.write(companyId, actor, "CRM_SALES_ORDER_CREATED", "CRM_SALES_ORDER", id.toString(), "{}");
        return new CreatedResource(id, "DRAFT");
    }

    @Transactional
    public CreatedResource decideOrder(UUID companyId, UUID orderId, DecisionRequest request, String actor) {
        ensureStatus(request.status(), Set.of("DRAFT", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED"));
        ensure("crm_sales_orders", companyId, orderId, "Sales order");
        jdbc.sql("update crm_sales_orders set status=:status,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("status", request.status()).param("companyId", companyId.toString()).param("id", orderId.toString()).update();
        audit.write(companyId, actor, "CRM_SALES_ORDER_STATUS_CHANGED", "CRM_SALES_ORDER", orderId.toString(), "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(orderId, request.status());
    }

    @Transactional
    public CreatedResource createReturn(UUID companyId, UUID orderId, SalesReturnRequest request, String actor) {
        ensure("crm_sales_orders", companyId, orderId, "Sales order");
        BigDecimal total = jdbc.sql("select total from crm_sales_orders where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", orderId.toString()).query(BigDecimal.class).single();
        if (request.amount().compareTo(total) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return amount exceeds order total");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into crm_sales_returns(id,company_id,sales_order_id,reason,amount) values(:id,:companyId,:orderId,:reason,:amount)")
            .param("id", id.toString()).param("companyId", companyId.toString()).param("orderId", orderId.toString())
            .param("reason", request.reason()).param("amount", request.amount()).update();
        audit.write(companyId, actor, "CRM_SALES_RETURN_CREATED", "CRM_SALES_RETURN", id.toString(), "{}");
        return new CreatedResource(id, "REQUESTED");
    }

    // Support

    public List<Map<String, Object>> tickets(UUID companyId, String status, UUID customerId, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        StringBuilder filter = new StringBuilder(" where t.company_id=:companyId");
        if (status != null && !status.isBlank()) { filter.append(" and t.status=:status"); params.put("status", status); }
        if (customerId != null) { filter.append(" and t.customer_id=:customerId"); params.put("customerId", customerId.toString()); }
        return rows("""
            select t.id,t.ticket_number,t.customer_id,c.display_name customer_name,t.contact_id,t.sales_order_id,t.subject,
                   t.description,t.channel,t.priority,t.status,t.assignee_employee_id,e.full_name assignee_name,t.sla_due_at,
                   t.first_response_at,t.resolved_at,t.resolution,t.satisfaction_score,t.created_at,t.updated_at,
                   case when t.sla_due_at<current_timestamp and t.status not in ('RESOLVED','CLOSED') then true else false end sla_breached
            from crm_support_tickets t join customers c on c.id=t.customer_id left join employees e on e.id=t.assignee_employee_id
            """ + filter + " order by case t.priority when 'CRITICAL' then 0 when 'URGENT' then 1 when 'HIGH' then 2 else 3 end,t.created_at desc", params).stream()
            .filter(row -> access.canAccessTicket(authentication, companyId, id(row.get("id")), "crm.support.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createTicket(UUID companyId, TicketRequest request, String actor) {
        ensure("customers", companyId, request.customerId(), "Customer");
        if (request.contactId() != null) ensure("crm_contacts", companyId, request.contactId(), "Contact");
        if (request.salesOrderId() != null) ensure("crm_sales_orders", companyId, request.salesOrderId(), "Sales order");
        ensureEmployee(companyId, request.assigneeEmployeeId());
        UUID id = UUID.randomUUID();
        String number = nextCode("crm_support_tickets", "ticket_number", "TKT", companyId);
        jdbc.sql("""
            insert into crm_support_tickets(id,company_id,ticket_number,customer_id,contact_id,sales_order_id,subject,
              description,channel,priority,status,assignee_employee_id,sla_due_at,created_by_subject)
            values(:id,:companyId,:number,:customer,:contact,:orderId,:subject,:description,:channel,:priority,'OPEN',:assignee,:slaDue,:actor)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("number", number)
            .param("customer", request.customerId().toString()).param("contact", uuid(request.contactId()))
            .param("orderId", uuid(request.salesOrderId())).param("subject", request.subject())
            .param("description", blank(request.description())).param("channel", request.channel()).param("priority", request.priority())
            .param("assignee", uuid(request.assigneeEmployeeId())).param("slaDue", request.slaDueAt()).param("actor", actor).update();
        audit.write(companyId, actor, "CRM_TICKET_CREATED", "CRM_TICKET", id.toString(), "{}");
        return new CreatedResource(id, "OPEN");
    }

    @Transactional
    public CreatedResource decideTicket(UUID companyId, UUID ticketId, TicketDecisionRequest request, String actor) {
        ensure("crm_support_tickets", companyId, ticketId, "Ticket");
        jdbc.sql("""
            update crm_support_tickets set status=:status,resolution=coalesce(:resolution,resolution),
              satisfaction_score=coalesce(:score,satisfaction_score),
              first_response_at=case when first_response_at is null and :status<>'OPEN' then current_timestamp else first_response_at end,
              resolved_at=case when :status in ('RESOLVED','CLOSED') then current_timestamp else resolved_at end,
              updated_at=current_timestamp where company_id=:companyId and id=:id
            """).param("status", request.status()).param("resolution", blank(request.resolution()))
            .param("score", request.satisfactionScore()).param("companyId", companyId.toString()).param("id", ticketId.toString()).update();
        audit.write(companyId, actor, "CRM_TICKET_STATUS_CHANGED", "CRM_TICKET", ticketId.toString(), "{\"status\":\"" + request.status() + "\"}");
        return new CreatedResource(ticketId, request.status());
    }

    public List<Map<String, Object>> ticketComments(UUID companyId, UUID ticketId) {
        return rows("select id,body,internal_note,author_subject,created_at from crm_ticket_comments where company_id=:companyId and ticket_id=:ticketId order by created_at",
            params(companyId, "ticketId", ticketId));
    }

    @Transactional
    public CreatedResource addTicketComment(UUID companyId, UUID ticketId, TicketCommentRequest request, String actor) {
        ensure("crm_support_tickets", companyId, ticketId, "Ticket");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into crm_ticket_comments(id,company_id,ticket_id,body,internal_note,author_subject) values(:id,:companyId,:ticketId,:body,:internal,:actor)")
            .param("id", id.toString()).param("companyId", companyId.toString()).param("ticketId", ticketId.toString())
            .param("body", request.body()).param("internal", Boolean.TRUE.equals(request.internalNote())).param("actor", actor).update();
        audit.write(companyId, actor, "CRM_TICKET_COMMENTED", "CRM_TICKET", ticketId.toString(), "{}");
        return new CreatedResource(id, "CREATED");
    }

    // Campaigns

    public List<Map<String, Object>> campaigns(UUID companyId, String status, Authentication authentication) {
        Map<String, Object> params = params(companyId);
        String filter = " where c.company_id=:companyId";
        if (status != null && !status.isBlank()) { filter += " and c.status=:status"; params.put("status", status); }
        return rows("""
            select c.id,c.code,c.name,c.campaign_type,c.channel,c.status,c.starts_on,c.ends_on,c.budget,c.actual_cost,
                   c.currency,c.leads_generated,c.conversions,c.owner_employee_id,e.full_name owner_name,c.objective,
                   case when c.leads_generated=0 then 0 else round(c.conversions*100.0/c.leads_generated,2) end conversion_rate,
                   c.created_at,c.updated_at
            from crm_campaigns c left join employees e on e.id=c.owner_employee_id
            """ + filter + " order by c.starts_on desc nulls last,c.created_at desc", params).stream()
            .filter(row -> access.canAccessCampaign(authentication, companyId, id(row.get("id")), "crm.marketing.read"))
            .toList();
    }

    @Transactional
    public CreatedResource createCampaign(UUID companyId, CampaignRequest request, String actor) {
        ensureEmployee(companyId, request.ownerEmployeeId());
        UUID ownerId = request.ownerEmployeeId() == null ? employeeForSubject(companyId, actor) : request.ownerEmployeeId();
        if (request.startsOn() != null && request.endsOn() != null && request.endsOn().isBefore(request.startsOn()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign end date must be on or after start date");
        UUID id = UUID.randomUUID();
        String code = blank(request.code()) == null ? nextCode("crm_campaigns", "code", "CMP", companyId) : request.code().trim();
        String currency = blank(request.currency()) == null ? companyCurrency(companyId) : request.currency().trim();
        jdbc.sql("""
            insert into crm_campaigns(id,company_id,code,name,campaign_type,channel,status,starts_on,ends_on,budget,
              actual_cost,currency,leads_generated,conversions,owner_employee_id,objective)
            values(:id,:companyId,:code,:name,:type,:channel,:status,:startsOn,:endsOn,:budget,:actualCost,:currency,:leads,:conversions,:owner,:objective)
            """).param("id", id.toString()).param("companyId", companyId.toString()).param("code", code)
            .param("name", request.name()).param("type", request.campaignType()).param("channel", blank(request.channel()))
            .param("status", request.status()).param("startsOn", request.startsOn()).param("endsOn", request.endsOn())
            .param("budget", zero(request.budget())).param("actualCost", zero(request.actualCost())).param("currency", currency)
            .param("leads", request.leadsGenerated() == null ? 0 : request.leadsGenerated())
            .param("conversions", request.conversions() == null ? 0 : request.conversions())
            .param("owner", uuid(ownerId)).param("objective", blank(request.objective())).update();
        audit.write(companyId, actor, "CRM_CAMPAIGN_CREATED", "CRM_CAMPAIGN", id.toString(), "{}");
        return new CreatedResource(id, request.status());
    }

    // Internals

    private void insertLineItem(String table, String parentColumn, UUID parentId, UUID companyId, LineItemRequest item) {
        if (!Set.of("crm_quote_items", "crm_sales_order_items").contains(table)) throw new IllegalArgumentException("Unsupported item table");
        BigDecimal total = lineTotal(item);
        String discountColumn = table.equals("crm_quote_items") ? ",discount_percent" : "";
        String discountValue = table.equals("crm_quote_items") ? ",:discount" : "";
        var statement = jdbc.sql("insert into " + table + "(id,company_id," + parentColumn + ",item_code,description,quantity,unit_price" + discountColumn + ",line_total) "
            + "values(:id,:companyId,:parent,:code,:description,:quantity,:unitPrice" + discountValue + ",:lineTotal)")
            .param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString()).param("parent", parentId.toString())
            .param("code", blank(item.itemCode())).param("description", item.description()).param("quantity", item.quantity())
            .param("unitPrice", item.unitPrice()).param("lineTotal", total);
        if (table.equals("crm_quote_items")) statement.param("discount", zero(item.discountPercent()));
        statement.update();
    }

    private BigDecimal lineTotal(LineItemRequest item) {
        BigDecimal gross = item.quantity().multiply(item.unitPrice());
        BigDecimal discount = gross.multiply(zero(item.discountPercent())).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return gross.subtract(discount);
    }

    private int stageProbability(UUID companyId, UUID stageId) {
        return jdbc.sql("select probability from crm_pipeline_stages where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", stageId.toString()).query(Integer.class)
            .optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pipeline stage not found"));
    }

    private String quoteTerms(UUID companyId, QuoteRequest request) {
        if (blank(request.terms()) != null) return request.terms().trim();
        if (request.templateId() == null) return null;
        return jdbc.sql("select default_terms from crm_quote_templates where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", request.templateId().toString())
            .query(String.class).optional().orElse(null);
    }

    private String companyCurrency(UUID companyId) {
        return jdbc.sql("select base_currency from companies where id=:id").param("id", companyId.toString())
            .query(String.class).single().trim();
    }

    private void ensureEmployee(UUID companyId, UUID employeeId) {
        if (employeeId != null) ensure("employees", companyId, employeeId, "Employee");
    }

    private UUID employeeForSubject(UUID companyId, String subject) {
        return jdbc.sql("select id from employees where company_id=:companyId and keycloak_subject=:subject and employment_status='ACTIVE'")
            .param("companyId", companyId.toString()).param("subject", subject).query(String.class).optional()
            .map(String::trim).map(UUID::fromString).orElse(null);
    }

    private void ensure(String table, UUID companyId, UUID id, String label) {
        if (!Set.of("customers", "employees", "crm_contacts", "crm_campaigns", "crm_leads", "crm_pipeline_stages", "crm_quote_templates",
                "crm_opportunities", "crm_quotes", "crm_contracts", "crm_sales_orders", "crm_support_tickets").contains(table))
            throw new IllegalArgumentException("Unsupported resource table");
        long found = jdbc.sql("select count(*) from " + table + " where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", id.toString()).query(Long.class).single();
        if (found == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found");
    }

    private String nextCode(String table, String column, String prefix, UUID companyId) {
        if (!Set.of("customers", "crm_leads", "crm_opportunities", "crm_quotes", "crm_contracts", "crm_sales_orders",
                "crm_support_tickets", "crm_campaigns").contains(table)) throw new IllegalArgumentException("Unsupported code table");
        long sequence = jdbc.sql("select count(*) from " + table + " where company_id=:companyId")
            .param("companyId", companyId.toString()).query(Long.class).single();
        String code;
        do {
            sequence++;
            code = prefix + "-" + String.format("%05d", sequence);
        } while (jdbc.sql("select count(*) from " + table + " where company_id=:companyId and " + column + "=:code")
            .param("companyId", companyId.toString()).param("code", code).query(Long.class).single() > 0);
        return code;
    }

    private List<Map<String, Object>> rows(String sql, Map<String, Object> params) {
        return namedJdbc.queryForList(sql, params);
    }

    private static Map<String, Object> params(UUID companyId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("companyId", companyId.toString());
        return params;
    }

    private static Map<String, Object> params(UUID companyId, String key, UUID value) {
        Map<String, Object> params = params(companyId);
        params.put(key, value.toString());
        return params;
    }

    private static void ensureStatus(String status, Set<String> allowed) {
        if (!allowed.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported status");
    }

    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String uuid(UUID value) { return value == null ? null : value.toString(); }
    private static UUID id(Object value) { return value == null ? null : UUID.fromString(value.toString().trim()); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static boolean bool(Object value) { return value instanceof Boolean b ? b : Boolean.parseBoolean(text(value)); }
    private static LocalDate date(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        return value == null ? null : LocalDate.parse(value.toString());
    }
    private static LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime d) return d;
        if (value instanceof java.sql.Timestamp d) return d.toLocalDateTime();
        return null;
    }
    private static String safe(String value) { return value == null ? "" : value.replace("\\", "").replace("\"", ""); }
}
