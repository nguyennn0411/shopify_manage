package com.company.erp.finance;

import static com.company.erp.finance.FinanceModels.*;

import com.company.erp.audit.AuditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class FinanceService {
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final AuditService audit;
    private final FinanceAccess access;

    public FinanceService(JdbcClient jdbc, NamedParameterJdbcTemplate namedJdbc, AuditService audit, FinanceAccess access) {
        this.jdbc = jdbc; this.namedJdbc = namedJdbc; this.audit = audit; this.access = access;
    }

    public FinanceSummary summary(UUID companyId, Authentication authentication) {
        List<Map<String,Object>> tx = transactions(companyId, null, authentication);
        List<Map<String,Object>> inv = invoices(companyId, null, null, authentication);
        List<Map<String,Object>> budgetRows = budgets(companyId, null, authentication);
        List<Map<String,Object>> advanceRows = advances(companyId, null, authentication);
        BigDecimal income = tx.stream().filter(r -> "RECEIPT".equals(text(r.get("transaction_type"))) && "POSTED".equals(text(r.get("status"))))
            .map(r -> decimal(r.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = tx.stream().filter(r -> "PAYMENT".equals(text(r.get("transaction_type"))) && "POSTED".equals(text(r.get("status"))))
            .map(r -> decimal(r.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receivables = inv.stream().filter(r -> "RECEIVABLE".equals(text(r.get("direction"))))
            .map(r -> decimal(r.get("outstanding_amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payables = inv.stream().filter(r -> "PAYABLE".equals(text(r.get("direction"))))
            .map(r -> decimal(r.get("outstanding_amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = budgetRows.stream().filter(r -> "ACTIVE".equals(text(r.get("status"))))
            .map(r -> decimal(r.get("available_amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        long pendingTx = tx.stream().filter(r -> Set.of("DRAFT","SUBMITTED","APPROVED").contains(text(r.get("status")))).count();
        long overdue = inv.stream().filter(r -> bool(r.get("overdue"))).count();
        long pendingAdvances = advanceRows.stream().filter(r -> !Set.of("REJECTED","SETTLED","CANCELLED").contains(text(r.get("status")))).count();
        return new FinanceSummary(income, expenses, income.subtract(expenses), receivables, payables,
            available, pendingTx, overdue, pendingAdvances);
    }

    public List<Map<String,Object>> categories(UUID companyId) {
        return rows("select id,code,name,category_type,active from finance_categories where company_id=:companyId and active=true order by category_type,name", params(companyId));
    }

    public List<Map<String,Object>> references(UUID companyId, String kind) {
        return switch (kind) {
            case "departments" -> rows("select id,code,name from departments where company_id=:companyId order by name", params(companyId));
            case "projects" -> rows("select id,code,name,status from pm_projects where company_id=:companyId order by name", params(companyId));
            case "employees" -> rows("select id,employee_code,full_name name from employees where company_id=:companyId and employment_status='ACTIVE' order by full_name", params(companyId));
            case "customers" -> rows("select id,customer_code code,display_name name from customers where company_id=:companyId and status='ACTIVE' order by display_name", params(companyId));
            case "suppliers" -> rows("select id,code,name from suppliers where company_id=:companyId and status='ACTIVE' order by name", params(companyId));
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported finance reference");
        };
    }

    public List<Map<String,Object>> budgets(UUID companyId, String status, Authentication authentication) {
        Map<String,Object> p=params(companyId); String filter=" where b.company_id=:companyId";
        if (notBlank(status)) { filter+=" and b.status=:status"; p.put("status",status); }
        return rows("""
            select b.id,b.code,b.name,b.department_id,d.name department_name,b.project_id,p.name project_name,
              b.category_id,c.name category_name,b.period_start,b.period_end,b.currency,b.planned_amount,
              b.committed_amount,b.actual_amount,(b.planned_amount-b.committed_amount-b.actual_amount) available_amount,
              b.status,b.notes,b.created_at,b.updated_at
            from finance_budgets b left join departments d on d.id=b.department_id left join pm_projects p on p.id=b.project_id
              left join finance_categories c on c.id=b.category_id
            """+filter+" order by b.period_start desc,b.name",p).stream()
            .filter(r->access.canAccessRecord(authentication,companyId,"finance_budgets",id(r.get("id")),"finance.budget.read")).toList();
    }

    @Transactional
    public CreatedResource createBudget(UUID companyId, BudgetRequest request, String actor) {
        if (request.periodEnd().isBefore(request.periodStart())) bad("Budget end date must be on or after start date");
        ensureOptional(companyId,"departments",request.departmentId(),"Department");
        ensureOptional(companyId,"pm_projects",request.projectId(),"Project");
        ensureOptional(companyId,"finance_categories",request.categoryId(),"Category");
        UUID id=UUID.randomUUID(); String code=blank(request.code())==null?nextCode("finance_budgets","code","BUD",companyId):request.code().trim();
        jdbc.sql("""
            insert into finance_budgets(id,company_id,code,name,department_id,project_id,category_id,period_start,period_end,
              currency,planned_amount,status,notes,created_by_subject)
            values(:id,:companyId,:code,:name,:department,:project,:category,:start,:end,:currency,:amount,'DRAFT',:notes,:actor)
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("code",code).param("name",request.name())
            .param("department",uuid(request.departmentId())).param("project",uuid(request.projectId())).param("category",uuid(request.categoryId()))
            .param("start",request.periodStart()).param("end",request.periodEnd()).param("currency",request.currency())
            .param("amount",request.plannedAmount()).param("notes",blank(request.notes())).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_BUDGET_CREATED","FINANCE_BUDGET",id.toString(),"{}");
        return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decideBudget(UUID companyId, UUID budgetId, DecisionRequest request, String actor) {
        ensureStatus(request.status(),Set.of("ACTIVE","CLOSED","CANCELLED")); ensure(companyId,"finance_budgets",budgetId,"Budget");
        String current = jdbc.sql("select status from finance_budgets where company_id=:companyId and id=:id")
            .param("companyId", companyId.toString()).param("id", budgetId.toString()).query(String.class).single().trim();
        boolean allowed = ("DRAFT".equals(current) && Set.of("ACTIVE", "CANCELLED").contains(request.status()))
            || ("ACTIVE".equals(current) && Set.of("CLOSED", "CANCELLED").contains(request.status()));
        if (!allowed) bad("Unsupported budget status transition");
        jdbc.sql("update finance_budgets set status=:status,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("status",request.status()).param("companyId",companyId.toString()).param("id",budgetId.toString()).update();
        audit.write(companyId,actor,"FINANCE_BUDGET_STATUS_CHANGED","FINANCE_BUDGET",budgetId.toString(),meta(request.status(),request.note()));
        return new CreatedResource(budgetId,request.status());
    }

    public List<Map<String,Object>> transactions(UUID companyId, String status, Authentication authentication) {
        Map<String,Object> p=params(companyId); String filter=" where t.company_id=:companyId";
        if(notBlank(status)){filter+=" and t.status=:status";p.put("status",status);}
        return rows("""
            select t.id,t.transaction_number,t.transaction_type,t.category_id,c.name category_name,t.department_id,d.name department_name,
              t.project_id,p.name project_name,t.customer_id,cu.display_name customer_name,t.supplier_id,s.name supplier_name,
              t.amount,t.currency,t.occurred_on,t.payment_method,t.reference,t.description,t.document_object_key,t.status,
              t.requested_by_subject,t.approved_by_subject,t.approved_at,t.posted_at,t.created_at
            from finance_cash_transactions t join finance_categories c on c.id=t.category_id
              left join departments d on d.id=t.department_id left join pm_projects p on p.id=t.project_id
              left join customers cu on cu.id=t.customer_id left join suppliers s on s.id=t.supplier_id
            """+filter+" order by t.occurred_on desc,t.created_at desc",p).stream()
            .filter(r->access.canAccessRecord(authentication,companyId,"finance_cash_transactions",id(r.get("id")),"finance.transaction.read")).toList();
    }

    @Transactional
    public CreatedResource createTransaction(UUID companyId, CashTransactionRequest request, String actor) {
        ensure(companyId,"finance_categories",request.categoryId(),"Category");
        String categoryType=jdbc.sql("select category_type from finance_categories where company_id=:companyId and id=:id")
            .param("companyId",companyId.toString()).param("id",request.categoryId().toString()).query(String.class).single().trim();
        if(("RECEIPT".equals(request.transactionType())&&!"INCOME".equals(categoryType))||("PAYMENT".equals(request.transactionType())&&!"EXPENSE".equals(categoryType))) bad("Transaction type does not match category");
        ensureOptional(companyId,"departments",request.departmentId(),"Department"); ensureOptional(companyId,"pm_projects",request.projectId(),"Project");
        ensureOptional(companyId,"customers",request.customerId(),"Customer"); ensureOptional(companyId,"suppliers",request.supplierId(),"Supplier");
        UUID id=UUID.randomUUID(); String number=nextCode("finance_cash_transactions","transaction_number","RECEIPT".equals(request.transactionType())?"PT":"PC",companyId);
        jdbc.sql("""
            insert into finance_cash_transactions(id,company_id,transaction_number,transaction_type,category_id,department_id,project_id,
              customer_id,supplier_id,amount,currency,occurred_on,payment_method,reference,description,document_object_key,status,requested_by_subject)
            values(:id,:companyId,:number,:type,:category,:department,:project,:customer,:supplier,:amount,:currency,:date,:method,:reference,:description,:document,'DRAFT',:actor)
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("type",request.transactionType())
            .param("category",request.categoryId().toString()).param("department",uuid(request.departmentId())).param("project",uuid(request.projectId()))
            .param("customer",uuid(request.customerId())).param("supplier",uuid(request.supplierId())).param("amount",request.amount())
            .param("currency",request.currency()).param("date",request.occurredOn()).param("method",blank(request.paymentMethod()))
            .param("reference",blank(request.reference())).param("description",request.description()).param("document",blank(request.documentObjectKey())).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_TRANSACTION_CREATED","FINANCE_TRANSACTION",id.toString(),"{\"amount\":"+request.amount()+"}");
        return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decideTransaction(UUID companyId, UUID transactionId, DecisionRequest request, String actor) {
        ensureStatus(request.status(),Set.of("SUBMITTED","APPROVED","REJECTED","POSTED","CANCELLED")); ensure(companyId,"finance_cash_transactions",transactionId,"Transaction");
        Map<String,Object> tx=one("select * from finance_cash_transactions where company_id=:companyId and id=:id",params(companyId,"id",transactionId));
        String current=text(tx.get("status"));
        boolean allowed=("DRAFT".equals(current)&&Set.of("SUBMITTED","CANCELLED").contains(request.status()))
            ||("SUBMITTED".equals(current)&&Set.of("APPROVED","REJECTED").contains(request.status()))
            ||("APPROVED".equals(current)&&"POSTED".equals(request.status()));
        if(!allowed) bad("Unsupported transaction status transition");
        if("POSTED".equals(request.status())&&"PAYMENT".equals(text(tx.get("transaction_type")))) applyBudget(companyId,tx);
        jdbc.sql("""
            update finance_cash_transactions set status=:status,
              approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,
              approved_at=case when :status='APPROVED' then current_timestamp else approved_at end,
              posted_at=case when :status='POSTED' then current_timestamp else posted_at end,updated_at=current_timestamp
            where company_id=:companyId and id=:id
            """).param("status",request.status()).param("actor",actor).param("companyId",companyId.toString()).param("id",transactionId.toString()).update();
        audit.write(companyId,actor,"FINANCE_TRANSACTION_STATUS_CHANGED","FINANCE_TRANSACTION",transactionId.toString(),meta(request.status(),request.note()));
        return new CreatedResource(transactionId,request.status());
    }

    public List<Map<String,Object>> invoices(UUID companyId, String direction, String status, Authentication authentication) {
        Map<String,Object> p=params(companyId); String filter=" where i.company_id=:companyId";
        if(notBlank(direction)){filter+=" and i.direction=:direction";p.put("direction",direction);}
        if(notBlank(status)){filter+=" and i.status=:status";p.put("status",status);}
        return rows("""
            select i.id,i.invoice_number,i.direction,i.customer_id,c.display_name customer_name,i.supplier_id,s.name supplier_name,
              i.sales_order_id,i.contract_id,i.project_id,p.name project_name,i.department_id,d.name department_name,
              i.issue_date,i.due_date,i.subtotal,i.tax_amount,i.total_amount,i.paid_amount,(i.total_amount-i.paid_amount) outstanding_amount,
              i.currency,i.status,i.description,i.document_object_key,i.created_at,i.updated_at,
              case when i.due_date<current_date and i.status not in ('PAID','CANCELLED') then true else false end overdue
            from finance_invoices i left join customers c on c.id=i.customer_id left join suppliers s on s.id=i.supplier_id
              left join pm_projects p on p.id=i.project_id left join departments d on d.id=i.department_id
            """+filter+" order by i.due_date,i.created_at desc",p).stream()
            .filter(r->access.canAccessRecord(authentication,companyId,"finance_invoices",id(r.get("id")),"finance.invoice.read")).toList();
    }

    @Transactional
    public CreatedResource createInvoice(UUID companyId, InvoiceRequest request, String actor) {
        if(request.dueDate().isBefore(request.issueDate())) bad("Invoice due date must be on or after issue date");
        if("RECEIVABLE".equals(request.direction())&&request.customerId()==null) bad("Receivable invoice requires a customer");
        if("PAYABLE".equals(request.direction())&&request.supplierId()==null) bad("Payable invoice requires a supplier");
        ensureOptional(companyId,"customers",request.customerId(),"Customer"); ensureOptional(companyId,"suppliers",request.supplierId(),"Supplier");
        ensureOptional(companyId,"crm_sales_orders",request.salesOrderId(),"Sales order"); ensureOptional(companyId,"crm_contracts",request.contractId(),"Contract");
        ensureOptional(companyId,"pm_projects",request.projectId(),"Project"); ensureOptional(companyId,"departments",request.departmentId(),"Department");
        BigDecimal tax=zero(request.taxAmount()); BigDecimal total=request.subtotal().add(tax);
        UUID id=UUID.randomUUID(); String number=blank(request.invoiceNumber())==null?nextCode("finance_invoices","invoice_number","RECEIVABLE".equals(request.direction())?"INV-OUT":"INV-IN",companyId):request.invoiceNumber().trim();
        jdbc.sql("""
            insert into finance_invoices(id,company_id,invoice_number,direction,customer_id,supplier_id,sales_order_id,contract_id,
              project_id,department_id,issue_date,due_date,subtotal,tax_amount,total_amount,currency,status,description,document_object_key,created_by_subject)
            values(:id,:companyId,:number,:direction,:customer,:supplier,:salesOrder,:contract,:project,:department,:issue,:due,:subtotal,:tax,:total,:currency,'OPEN',:description,:document,:actor)
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("direction",request.direction())
            .param("customer",uuid(request.customerId())).param("supplier",uuid(request.supplierId())).param("salesOrder",uuid(request.salesOrderId()))
            .param("contract",uuid(request.contractId())).param("project",uuid(request.projectId())).param("department",uuid(request.departmentId()))
            .param("issue",request.issueDate()).param("due",request.dueDate()).param("subtotal",request.subtotal()).param("tax",tax).param("total",total)
            .param("currency",request.currency()).param("description",blank(request.description())).param("document",blank(request.documentObjectKey())).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_INVOICE_CREATED","FINANCE_INVOICE",id.toString(),"{\"total\":"+total+"}");
        return new CreatedResource(id,"OPEN");
    }

    public List<Map<String,Object>> invoicePayments(UUID companyId, UUID invoiceId) {
        return rows("select id,amount,paid_on,payment_method,reference,notes,recorded_by_subject,created_at from finance_invoice_payments where company_id=:companyId and invoice_id=:invoiceId order by paid_on,created_at",params(companyId,"invoiceId",invoiceId));
    }

    @Transactional
    public CreatedResource addInvoicePayment(UUID companyId, UUID invoiceId, InvoicePaymentRequest request, String actor) {
        ensure(companyId,"finance_invoices",invoiceId,"Invoice");
        Map<String,Object> invoice=one("select total_amount,paid_amount,status from finance_invoices where company_id=:companyId and id=:id for update",params(companyId,"id",invoiceId));
        if(Set.of("PAID","CANCELLED").contains(text(invoice.get("status")))) bad("Invoice cannot receive another payment");
        BigDecimal paid=decimal(invoice.get("paid_amount")).add(request.amount()), total=decimal(invoice.get("total_amount"));
        if(paid.compareTo(total)>0) bad("Payment cannot exceed invoice outstanding amount");
        UUID id=UUID.randomUUID(); String status=paid.compareTo(total)==0?"PAID":"PARTIALLY_PAID";
        jdbc.sql("insert into finance_invoice_payments(id,company_id,invoice_id,amount,paid_on,payment_method,reference,notes,recorded_by_subject) values(:id,:companyId,:invoice,:amount,:paidOn,:method,:reference,:notes,:actor)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("invoice",invoiceId.toString()).param("amount",request.amount())
            .param("paidOn",request.paidOn()).param("method",blank(request.paymentMethod())).param("reference",blank(request.reference())).param("notes",blank(request.notes())).param("actor",actor).update();
        jdbc.sql("update finance_invoices set paid_amount=:paid,status=:status,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("paid",paid).param("status",status).param("companyId",companyId.toString()).param("id",invoiceId.toString()).update();
        audit.write(companyId,actor,"FINANCE_INVOICE_PAYMENT_RECORDED","FINANCE_INVOICE",invoiceId.toString(),"{\"amount\":"+request.amount()+"}");
        return new CreatedResource(id,status);
    }

    public List<Map<String,Object>> advances(UUID companyId, String status, Authentication authentication) {
        Map<String,Object> p=params(companyId); String filter=" where a.company_id=:companyId";
        if(notBlank(status)){filter+=" and a.status=:status";p.put("status",status);}
        return rows("""
            select a.id,a.advance_number,a.employee_id,e.full_name employee_name,a.department_id,d.name department_name,
              a.project_id,p.name project_name,a.purpose,a.requested_amount,a.disbursed_amount,a.expense_amount,a.returned_amount,
              (a.disbursed_amount-a.expense_amount-a.returned_amount) remaining_amount,a.currency,a.needed_on,a.status,
              a.requested_by_subject,a.approved_by_subject,a.approved_at,a.settled_at,a.created_at,a.updated_at
            from finance_advances a join employees e on e.id=a.employee_id left join departments d on d.id=a.department_id
              left join pm_projects p on p.id=a.project_id
            """+filter+" order by a.needed_on desc,a.created_at desc",p).stream()
            .filter(r->access.canAccessRecord(authentication,companyId,"finance_advances",id(r.get("id")),"finance.advance.read")).toList();
    }

    @Transactional
    public CreatedResource createAdvance(UUID companyId, AdvanceRequest request, String actor) {
        ensure(companyId,"employees",request.employeeId(),"Employee"); ensureOptional(companyId,"departments",request.departmentId(),"Department"); ensureOptional(companyId,"pm_projects",request.projectId(),"Project");
        UUID id=UUID.randomUUID(); String number=nextCode("finance_advances","advance_number","ADV",companyId);
        jdbc.sql("""
            insert into finance_advances(id,company_id,advance_number,employee_id,department_id,project_id,purpose,requested_amount,
              currency,needed_on,status,requested_by_subject)
            values(:id,:companyId,:number,:employee,:department,:project,:purpose,:amount,:currency,:neededOn,'DRAFT',:actor)
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("employee",request.employeeId().toString())
            .param("department",uuid(request.departmentId())).param("project",uuid(request.projectId())).param("purpose",request.purpose())
            .param("amount",request.requestedAmount()).param("currency",request.currency()).param("neededOn",request.neededOn()).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_ADVANCE_CREATED","FINANCE_ADVANCE",id.toString(),"{}"); return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource decideAdvance(UUID companyId, UUID advanceId, DecisionRequest request, String actor) {
        ensureStatus(request.status(),Set.of("SUBMITTED","APPROVED","REJECTED","DISBURSED","CANCELLED")); ensure(companyId,"finance_advances",advanceId,"Advance");
        Map<String,Object> a=one("select status,requested_amount from finance_advances where company_id=:companyId and id=:id for update",params(companyId,"id",advanceId));
        String current=text(a.get("status")); boolean allowed=("DRAFT".equals(current)&&Set.of("SUBMITTED","CANCELLED").contains(request.status()))
            ||("SUBMITTED".equals(current)&&Set.of("APPROVED","REJECTED").contains(request.status()))||("APPROVED".equals(current)&&"DISBURSED".equals(request.status()));
        if(!allowed) bad("Unsupported advance status transition");
        jdbc.sql("""
            update finance_advances set status=:status,
              approved_by_subject=case when :status='APPROVED' then :actor else approved_by_subject end,
              approved_at=case when :status='APPROVED' then current_timestamp else approved_at end,
              disbursed_amount=case when :status='DISBURSED' then requested_amount else disbursed_amount end,updated_at=current_timestamp
            where company_id=:companyId and id=:id
            """).param("status",request.status()).param("actor",actor).param("companyId",companyId.toString()).param("id",advanceId.toString()).update();
        audit.write(companyId,actor,"FINANCE_ADVANCE_STATUS_CHANGED","FINANCE_ADVANCE",advanceId.toString(),meta(request.status(),request.note())); return new CreatedResource(advanceId,request.status());
    }

    public List<Map<String,Object>> advanceExpenses(UUID companyId, UUID advanceId) {
        return rows("select x.id,x.category_id,c.name category_name,x.amount,x.incurred_on,x.description,x.document_object_key,x.created_at from finance_advance_expenses x join finance_categories c on c.id=x.category_id where x.company_id=:companyId and x.advance_id=:advanceId order by x.incurred_on",params(companyId,"advanceId",advanceId));
    }

    @Transactional
    public CreatedResource addAdvanceExpense(UUID companyId, UUID advanceId, AdvanceExpenseRequest request, String actor) {
        ensure(companyId,"finance_advances",advanceId,"Advance"); ensure(companyId,"finance_categories",request.categoryId(),"Category");
        Map<String,Object>a=one("select status,disbursed_amount,expense_amount from finance_advances where company_id=:companyId and id=:id for update",params(companyId,"id",advanceId));
        if(!"DISBURSED".equals(text(a.get("status")))) bad("Advance must be disbursed before settlement expenses are added");
        BigDecimal expense=decimal(a.get("expense_amount")).add(request.amount()); if(expense.compareTo(decimal(a.get("disbursed_amount")))>0) bad("Advance expenses cannot exceed disbursed amount");
        UUID id=UUID.randomUUID(); jdbc.sql("insert into finance_advance_expenses(id,company_id,advance_id,category_id,amount,incurred_on,description,document_object_key) values(:id,:companyId,:advance,:category,:amount,:date,:description,:document)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("advance",advanceId.toString()).param("category",request.categoryId().toString())
            .param("amount",request.amount()).param("date",request.incurredOn()).param("description",request.description()).param("document",blank(request.documentObjectKey())).update();
        jdbc.sql("update finance_advances set expense_amount=:expense,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("expense",expense).param("companyId",companyId.toString()).param("id",advanceId.toString()).update();
        audit.write(companyId,actor,"FINANCE_ADVANCE_EXPENSE_ADDED","FINANCE_ADVANCE",advanceId.toString(),"{\"amount\":"+request.amount()+"}"); return new CreatedResource(id,"RECORDED");
    }

    @Transactional
    public CreatedResource settleAdvance(UUID companyId, UUID advanceId, AdvanceSettlementRequest request, String actor) {
        ensure(companyId,"finance_advances",advanceId,"Advance"); Map<String,Object>a=one("select status,disbursed_amount,expense_amount from finance_advances where company_id=:companyId and id=:id for update",params(companyId,"id",advanceId));
        if(!"DISBURSED".equals(text(a.get("status")))) bad("Only disbursed advances can be settled"); BigDecimal returned=zero(request.returnedAmount());
        if(decimal(a.get("expense_amount")).add(returned).compareTo(decimal(a.get("disbursed_amount")))!=0) bad("Expenses plus returned amount must equal disbursed amount");
        jdbc.sql("update finance_advances set returned_amount=:returned,status='SETTLED',settled_at=current_timestamp,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("returned",returned).param("companyId",companyId.toString()).param("id",advanceId.toString()).update();
        audit.write(companyId,actor,"FINANCE_ADVANCE_SETTLED","FINANCE_ADVANCE",advanceId.toString(),"{\"returned\":"+returned+"}"); return new CreatedResource(advanceId,"SETTLED");
    }

    public List<Map<String,Object>> projectCosts(UUID companyId, UUID projectId, Authentication authentication) {
        Map<String,Object> p=params(companyId); String filter=" where x.company_id=:companyId"; if(projectId!=null){filter+=" and x.project_id=:projectId";p.put("projectId",projectId.toString());}
        return rows("select x.id,x.project_id,p.name project_name,x.category_id,c.name category_name,x.cost_type,x.amount,x.currency,x.incurred_on,x.source_type,x.source_id,x.description,x.status,x.created_at from finance_project_costs x join pm_projects p on p.id=x.project_id join finance_categories c on c.id=x.category_id"+filter+" order by x.incurred_on desc",p).stream()
            .filter(r -> access.canAccessProject(authentication, companyId, id(r.get("project_id")), "finance.project-cost.read")).toList();
    }

    @Transactional
    public CreatedResource createProjectCost(UUID companyId, ProjectCostRequest request, String actor) {
        ensure(companyId,"pm_projects",request.projectId(),"Project"); ensure(companyId,"finance_categories",request.categoryId(),"Category");
        Map<String,Object> budgetData=new LinkedHashMap<>(); budgetData.put("project_id",request.projectId().toString()); budgetData.put("department_id",null); budgetData.put("category_id",request.categoryId().toString()); budgetData.put("amount",request.amount()); budgetData.put("currency",request.currency()); budgetData.put("occurred_on",request.incurredOn()); applyBudget(companyId,budgetData);
        UUID id=UUID.randomUUID(); jdbc.sql("insert into finance_project_costs(id,company_id,project_id,category_id,cost_type,amount,currency,incurred_on,source_type,source_id,description,status,created_by_subject) values(:id,:companyId,:project,:category,:type,:amount,:currency,:date,:sourceType,:sourceId,:description,'POSTED',:actor)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("project",request.projectId().toString()).param("category",request.categoryId().toString())
            .param("type",request.costType()).param("amount",request.amount()).param("currency",request.currency()).param("date",request.incurredOn())
            .param("sourceType",blank(request.sourceType())).param("sourceId",uuid(request.sourceId())).param("description",request.description()).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_PROJECT_COST_CREATED","FINANCE_PROJECT_COST",id.toString(),"{\"amount\":"+request.amount()+"}"); return new CreatedResource(id,"POSTED");
    }

    public List<Map<String,Object>> accounts(UUID companyId) {
        return rows("select a.id,a.code,a.name,a.account_type,a.parent_account_id,p.name parent_name,a.active from finance_accounts a left join finance_accounts p on p.id=a.parent_account_id where a.company_id=:companyId and a.active=true order by a.code",params(companyId));
    }

    @Transactional
    public CreatedResource createAccount(UUID companyId, AccountRequest request, String actor) {
        ensureOptional(companyId,"finance_accounts",request.parentAccountId(),"Parent account"); UUID id=UUID.randomUUID();
        jdbc.sql("insert into finance_accounts(id,company_id,code,name,account_type,parent_account_id) values(:id,:companyId,:code,:name,:type,:parent)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("code",request.code()).param("name",request.name()).param("type",request.accountType()).param("parent",uuid(request.parentAccountId())).update();
        audit.write(companyId,actor,"FINANCE_ACCOUNT_CREATED","FINANCE_ACCOUNT",id.toString(),"{}"); return new CreatedResource(id,"ACTIVE");
    }

    public List<Map<String,Object>> journals(UUID companyId, String status) {
        Map<String,Object>p=params(companyId);String filter=" where j.company_id=:companyId";if(notBlank(status)){filter+=" and j.status=:status";p.put("status",status);}
        return rows("select j.id,j.journal_number,j.journal_date,j.description,j.reference,j.status,j.created_by_subject,j.posted_by_subject,j.posted_at,j.created_at,coalesce(sum(l.debit),0) total_debit,coalesce(sum(l.credit),0) total_credit from finance_journals j left join finance_journal_lines l on l.journal_id=j.id"+filter+" group by j.id order by j.journal_date desc,j.created_at desc",p);
    }

    public List<Map<String,Object>> journalLines(UUID companyId, UUID journalId) {
        return rows("select l.id,l.account_id,a.code account_code,a.name account_name,l.department_id,d.name department_name,l.project_id,p.name project_name,l.description,l.debit,l.credit from finance_journal_lines l join finance_accounts a on a.id=l.account_id left join departments d on d.id=l.department_id left join pm_projects p on p.id=l.project_id where l.company_id=:companyId and l.journal_id=:journalId order by l.id",params(companyId,"journalId",journalId));
    }

    @Transactional
    public CreatedResource createJournal(UUID companyId, JournalRequest request, String actor) {
        BigDecimal debit=request.lines().stream().map(l->zero(l.debit())).reduce(BigDecimal.ZERO,BigDecimal::add), credit=request.lines().stream().map(l->zero(l.credit())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(debit.signum()<=0||debit.compareTo(credit)!=0) bad("Journal debit and credit totals must be equal and greater than zero");
        UUID id=UUID.randomUUID();String number=nextCode("finance_journals","journal_number","JV",companyId);
        jdbc.sql("insert into finance_journals(id,company_id,journal_number,journal_date,description,reference,status,created_by_subject) values(:id,:companyId,:number,:date,:description,:reference,'DRAFT',:actor)")
            .param("id",id.toString()).param("companyId",companyId.toString()).param("number",number).param("date",request.journalDate()).param("description",request.description()).param("reference",blank(request.reference())).param("actor",actor).update();
        for(JournalLineRequest line:request.lines()){
            ensure(companyId,"finance_accounts",line.accountId(),"Account");ensureOptional(companyId,"departments",line.departmentId(),"Department");ensureOptional(companyId,"pm_projects",line.projectId(),"Project");
            BigDecimal lineDebit=zero(line.debit()),lineCredit=zero(line.credit());if((lineDebit.signum()>0)==(lineCredit.signum()>0))bad("Each journal line must contain either debit or credit");
            jdbc.sql("insert into finance_journal_lines(id,company_id,journal_id,account_id,department_id,project_id,description,debit,credit) values(:id,:companyId,:journal,:account,:department,:project,:description,:debit,:credit)")
                .param("id",UUID.randomUUID().toString()).param("companyId",companyId.toString()).param("journal",id.toString()).param("account",line.accountId().toString())
                .param("department",uuid(line.departmentId())).param("project",uuid(line.projectId())).param("description",blank(line.description())).param("debit",lineDebit).param("credit",lineCredit).update();
        }
        audit.write(companyId,actor,"FINANCE_JOURNAL_CREATED","FINANCE_JOURNAL",id.toString(),"{\"total\":"+debit+"}");return new CreatedResource(id,"DRAFT");
    }

    @Transactional
    public CreatedResource postJournal(UUID companyId, UUID journalId, String actor) {
        ensure(companyId,"finance_journals",journalId,"Journal"); Map<String,Object>j=one("select journal_date,status from finance_journals where company_id=:companyId and id=:id for update",params(companyId,"id",journalId));
        if(!"DRAFT".equals(text(j.get("status"))))bad("Only draft journals can be posted");LocalDate date=date(j.get("journal_date"));
        long closed=jdbc.sql("select count(*) from finance_periods where company_id=:companyId and period_year=:year and period_month=:month and status='CLOSED'")
            .param("companyId",companyId.toString()).param("year",date.getYear()).param("month",date.getMonthValue()).query(Long.class).single();if(closed>0)bad("Accounting period is closed");
        jdbc.sql("update finance_journals set status='POSTED',posted_by_subject=:actor,posted_at=current_timestamp where company_id=:companyId and id=:id")
            .param("actor",actor).param("companyId",companyId.toString()).param("id",journalId.toString()).update();
        audit.write(companyId,actor,"FINANCE_JOURNAL_POSTED","FINANCE_JOURNAL",journalId.toString(),"{}");return new CreatedResource(journalId,"POSTED");
    }

    public List<Map<String,Object>> periods(UUID companyId) {return rows("select id,period_year,period_month,status,closed_by_subject,closed_at from finance_periods where company_id=:companyId order by period_year desc,period_month desc",params(companyId));}

    @Transactional
    public CreatedResource setPeriod(UUID companyId, PeriodRequest request, String actor) {
        String existing=jdbc.sql("select id from finance_periods where company_id=:companyId and period_year=:year and period_month=:month")
            .param("companyId",companyId.toString()).param("year",request.year()).param("month",request.month()).query(String.class).optional().orElse(null);
        UUID id=existing==null?UUID.randomUUID():UUID.fromString(existing.trim());
        jdbc.sql("""
            insert into finance_periods(id,company_id,period_year,period_month,status,closed_by_subject,closed_at)
            values(:id,:companyId,:year,:month,:status,:actor,case when :status='CLOSED' then current_timestamp else null end)
            on conflict(company_id,period_year,period_month) do update set status=excluded.status,
              closed_by_subject=case when excluded.status='CLOSED' then excluded.closed_by_subject else null end,
              closed_at=case when excluded.status='CLOSED' then current_timestamp else null end
            """).param("id",id.toString()).param("companyId",companyId.toString()).param("year",request.year()).param("month",request.month()).param("status",request.status()).param("actor",actor).update();
        audit.write(companyId,actor,"FINANCE_PERIOD_"+request.status(),"FINANCE_PERIOD",id.toString(),"{}");return new CreatedResource(id,request.status());
    }

    public List<Map<String,Object>> monthlyReport(UUID companyId, LocalDate from, LocalDate to) {
        LocalDate start=from==null?LocalDate.now().withDayOfMonth(1).minusMonths(11):from;LocalDate end=to==null?LocalDate.now():to;if(end.isBefore(start))bad("Report end date must be on or after start date");
        Map<String,Object>p=params(companyId);p.put("from",start);p.put("to",end);
        return rows("""
            select date_trunc('month',occurred_on)::date month,
              sum(case when transaction_type='RECEIPT' then amount else 0 end) income,
              sum(case when transaction_type='PAYMENT' then amount else 0 end) expenses,
              sum(case when transaction_type='RECEIPT' then amount else -amount end) profit,
              currency
            from finance_cash_transactions where company_id=:companyId and status='POSTED' and occurred_on between :from and :to
            group by date_trunc('month',occurred_on),currency order by month
            """,p);
    }

    public List<Map<String,Object>> generalLedger(UUID companyId, LocalDate from, LocalDate to) {
        Map<String,Object>p=params(companyId);p.put("from",from==null?LocalDate.of(2000,1,1):from);p.put("to",to==null?LocalDate.now():to);
        return rows("""
            select a.code account_code,a.name account_name,a.account_type,sum(l.debit) debit,sum(l.credit) credit,
              sum(l.debit-l.credit) balance
            from finance_journal_lines l join finance_journals j on j.id=l.journal_id join finance_accounts a on a.id=l.account_id
            where l.company_id=:companyId and j.status='POSTED' and j.journal_date between :from and :to
            group by a.id,a.code,a.name,a.account_type order by a.code
            """,p);
    }

    private void applyBudget(UUID companyId, Map<String,Object> source) {
        Map<String,Object>p=params(companyId);p.put("date",date(source.get("occurred_on")));p.put("currency",text(source.get("currency")));p.put("amount",decimal(source.get("amount")));
        p.put("department",textOrNull(source.get("department_id")));p.put("project",textOrNull(source.get("project_id")));p.put("category",textOrNull(source.get("category_id")));
        List<Map<String,Object>>matches=rows("""
            select id,planned_amount,committed_amount,actual_amount from finance_budgets where company_id=:companyId and status='ACTIVE'
              and :date between period_start and period_end and currency=:currency
              and (department_id is null or department_id=:department) and (project_id is null or project_id=:project)
              and (category_id is null or category_id=:category)
            order by case when category_id is null then 1 else 0 end,case when project_id is null and department_id is null then 1 else 0 end limit 1 for update
            """,p);
        if(matches.isEmpty())return;Map<String,Object>b=matches.getFirst();BigDecimal actual=decimal(b.get("actual_amount")).add(decimal(source.get("amount")));
        if(actual.add(decimal(b.get("committed_amount"))).compareTo(decimal(b.get("planned_amount")))>0)bad("Expense exceeds active budget limit");
        jdbc.sql("update finance_budgets set actual_amount=:actual,updated_at=current_timestamp where company_id=:companyId and id=:id")
            .param("actual",actual).param("companyId",companyId.toString()).param("id",text(b.get("id"))).update();
    }

    private void ensureOptional(UUID companyId,String table,UUID id,String label){if(id!=null)ensure(companyId,table,id,label);}
    private void ensure(UUID companyId,String table,UUID id,String label){if(!Set.of("departments","pm_projects","customers","suppliers","employees","crm_sales_orders","crm_contracts","finance_categories","finance_budgets","finance_cash_transactions","finance_invoices","finance_advances","finance_accounts","finance_journals").contains(table))throw new IllegalArgumentException("Unsupported finance resource table");long found=jdbc.sql("select count(*) from "+table+" where company_id=:companyId and id=:id").param("companyId",companyId.toString()).param("id",id.toString()).query(Long.class).single();if(found==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,label+" not found");}
    private String nextCode(String table,String column,String prefix,UUID companyId){if(!Set.of("finance_budgets","finance_cash_transactions","finance_invoices","finance_advances","finance_journals").contains(table))throw new IllegalArgumentException("Unsupported finance code table");long n=jdbc.sql("select count(*) from "+table+" where company_id=:companyId").param("companyId",companyId.toString()).query(Long.class).single();String code;do{code=prefix+"-"+String.format("%05d",++n);}while(jdbc.sql("select count(*) from "+table+" where company_id=:companyId and "+column+"=:code").param("companyId",companyId.toString()).param("code",code).query(Long.class).single()>0);return code;}
    private List<Map<String,Object>> rows(String sql,Map<String,Object>params){return namedJdbc.queryForList(sql,params);}private Map<String,Object>one(String sql,Map<String,Object>params){List<Map<String,Object>>r=rows(sql,params);if(r.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Finance record not found");return r.getFirst();}
    private static Map<String,Object>params(UUID companyId){Map<String,Object>p=new LinkedHashMap<>();p.put("companyId",companyId.toString());return p;}private static Map<String,Object>params(UUID companyId,String key,UUID value){Map<String,Object>p=params(companyId);p.put(key,value.toString());return p;}
    private static void ensureStatus(String status,Set<String>allowed){if(!allowed.contains(status))bad("Unsupported status");}private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}private static boolean notBlank(String v){return v!=null&&!v.isBlank();}private static String uuid(UUID v){return v==null?null:v.toString();}private static UUID id(Object v){return v==null?null:UUID.fromString(v.toString().trim());}private static String text(Object v){return v==null?"":v.toString();}private static String textOrNull(Object v){String s=text(v);return s.isBlank()?null:s.trim();}
    private static BigDecimal decimal(Object v){return v==null?BigDecimal.ZERO:new BigDecimal(v.toString());}private static BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}private static boolean bool(Object v){return v instanceof Boolean b?b:Boolean.parseBoolean(text(v));}
    private static LocalDate date(Object v){if(v instanceof LocalDate d)return d;if(v instanceof java.sql.Date d)return d.toLocalDate();return v==null?null:LocalDate.parse(v.toString());}
    private static String meta(String status,String note){return "{\"status\":\""+status+"\",\"note\":\""+(note==null?"":note.replace("\\","").replace("\"",""))+"\"}";}
}
