package com.company.erp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.erp.company.CompanyReferenceDataProvisioner;
import com.company.erp.company.CompanyAccess;
import com.company.erp.config.DevelopmentDataSeeder;
import com.company.erp.audit.AuditService;
import com.company.erp.automation.AutomationModels.ConnectionRequest;
import com.company.erp.automation.AutomationModels.WorkflowRequest;
import com.company.erp.automation.AutomationModels.WorkflowStepRequest;
import com.company.erp.automation.AutomationService;
import com.company.erp.crm.CrmAccess;
import com.company.erp.crm.CrmModels.CampaignRequest;
import com.company.erp.crm.CrmModels.ContractPaymentRequest;
import com.company.erp.crm.CrmModels.ContractRequest;
import com.company.erp.crm.CrmModels.CustomerRequest;
import com.company.erp.crm.CrmModels.LineItemRequest;
import com.company.erp.crm.CrmModels.OpportunityRequest;
import com.company.erp.crm.CrmModels.QuoteRequest;
import com.company.erp.crm.CrmModels.QuoteTemplateRequest;
import com.company.erp.crm.CrmModels.SalesOrderRequest;
import com.company.erp.crm.CrmModels.TicketRequest;
import com.company.erp.crm.CrmService;
import com.company.erp.finance.FinanceAccess;
import com.company.erp.finance.FinanceModels.AdvanceExpenseRequest;
import com.company.erp.finance.FinanceModels.AdvanceRequest;
import com.company.erp.finance.FinanceModels.AdvanceSettlementRequest;
import com.company.erp.finance.FinanceModels.BudgetRequest;
import com.company.erp.finance.FinanceModels.CashTransactionRequest;
import com.company.erp.finance.FinanceModels.DecisionRequest;
import com.company.erp.finance.FinanceModels.InvoicePaymentRequest;
import com.company.erp.finance.FinanceModels.InvoiceRequest;
import com.company.erp.finance.FinanceModels.JournalLineRequest;
import com.company.erp.finance.FinanceModels.JournalRequest;
import com.company.erp.finance.FinanceModels.PeriodRequest;
import com.company.erp.finance.FinanceModels.ProjectCostRequest;
import com.company.erp.finance.FinanceService;
import com.company.erp.operations.OperationsAccess;
import com.company.erp.operations.OperationsModels.AssetAssignmentRequest;
import com.company.erp.operations.OperationsModels.AssetRequest;
import com.company.erp.operations.OperationsModels.AssetReturnRequest;
import com.company.erp.operations.OperationsModels.GoodsReceiptRequest;
import com.company.erp.operations.OperationsModels.InventoryItemRequest;
import com.company.erp.operations.OperationsModels.LicenseRequest;
import com.company.erp.operations.OperationsModels.MaintenanceRequest;
import com.company.erp.operations.OperationsModels.ManufacturingProgressRequest;
import com.company.erp.operations.OperationsModels.ManufacturingRequest;
import com.company.erp.operations.OperationsModels.PurchaseOrderItemRequest;
import com.company.erp.operations.OperationsModels.PurchaseOrderRequest;
import com.company.erp.operations.OperationsModels.PurchaseRequestCreate;
import com.company.erp.operations.OperationsModels.PurchaseRequestItemRequest;
import com.company.erp.operations.OperationsModels.ReceiptLineRequest;
import com.company.erp.operations.OperationsModels.ShipmentRequest;
import com.company.erp.operations.OperationsModels.StockCountLineRequest;
import com.company.erp.operations.OperationsModels.StockCountRequest;
import com.company.erp.operations.OperationsModels.SupplierRequest;
import com.company.erp.operations.OperationsService;
import com.company.erp.productivity.ProductivityModels.NotificationRequest;
import com.company.erp.productivity.ProductivityModels.SavedViewRequest;
import com.company.erp.productivity.ProductivityService;
import com.company.erp.reporting.ReportingAccess;
import com.company.erp.reporting.ReportingModels.KeyResultRequest;
import com.company.erp.reporting.ReportingModels.ObjectiveRequest;
import com.company.erp.reporting.ReportingModels.ProgressRequest;
import com.company.erp.reporting.ReportingModels.SnapshotRequest;
import com.company.erp.reporting.ReportingService;
import com.company.erp.workplace.WorkplaceAccess;
import com.company.erp.workplace.WorkplaceModels.ApprovalRequest;
import com.company.erp.workplace.WorkplaceModels.ApproverRequest;
import com.company.erp.workplace.WorkplaceModels.ChannelRequest;
import com.company.erp.workplace.WorkplaceModels.DocumentRequest;
import com.company.erp.workplace.WorkplaceModels.DocumentVersionRequest;
import com.company.erp.workplace.WorkplaceModels.FormDefinitionRequest;
import com.company.erp.workplace.WorkplaceModels.FormSubmissionRequest;
import com.company.erp.workplace.WorkplaceModels.HelpdeskCommentRequest;
import com.company.erp.workplace.WorkplaceModels.HelpdeskTicketRequest;
import com.company.erp.workplace.WorkplaceModels.KnowledgeArticleRequest;
import com.company.erp.workplace.WorkplaceModels.LegalObligationRequest;
import com.company.erp.workplace.WorkplaceModels.LegalRecordRequest;
import com.company.erp.workplace.WorkplaceModels.MessageRequest;
import com.company.erp.workplace.WorkplaceModels.ScanStatusRequest;
import com.company.erp.workplace.WorkplaceModels.SignatureDecisionRequest;
import com.company.erp.workplace.WorkplaceModels.SignatureRequest;
import com.company.erp.workplace.WorkplaceModels.StatusRequest;
import com.company.erp.workplace.WorkplaceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

class FlywayMigrationIT {
    @Test
    void migrationsAndCompanyReferenceDataAreRepeatable() {
        try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine")) {
            postgres.start();
            var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .defaultSchema("erp_v2").schemas("erp_v2").createSchemas(true)
                .locations("classpath:db/migration").validateMigrationNaming(true).load();
            var result = flyway.migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(16);
            assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

            String jdbcUrl = postgres.getJdbcUrl();
            String querySeparator = jdbcUrl.contains("?") ? "&" : "?";
            var dataSource = new DriverManagerDataSource(
                jdbcUrl + querySeparator + "currentSchema=erp_v2",
                postgres.getUsername(),
                postgres.getPassword()
            );
            var jdbc = JdbcClient.create(dataSource);
            var provisioner = new CompanyReferenceDataProvisioner(jdbc);
            var seeder = new DevelopmentDataSeeder(
                jdbc,
                provisioner,
                "DEMO",
                "Demo Company",
                "dev-user-subject",
                "developer@example.test",
                "Development User"
            );
            seeder.run(null);
            seeder.run(null);
            UUID companyId = UUID.fromString(jdbc.sql("select id from companies where code='DEMO'")
                .query(String.class).single().trim());

            assertThat(jdbc.sql("select count(*) from companies where code='DEMO'").query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from erp_users where keycloak_subject='dev-user-subject'").query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from company_memberships where company_id=:companyId")
                .param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(count(jdbc, "departments", companyId)).isEqualTo(3);
            assertThat(count(jdbc, "leave_types", companyId)).isEqualTo(3);
            assertThat(count(jdbc, "work_shifts", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "pm_workflow_statuses", companyId)).isEqualTo(9);
            assertThat(count(jdbc, "company_roles", companyId)).isEqualTo(13);
            assertThat(count(jdbc, "crm_pipeline_stages", companyId)).isEqualTo(6);
            assertThat(count(jdbc, "finance_categories", companyId)).isEqualTo(9);
            assertThat(count(jdbc, "finance_accounts", companyId)).isEqualTo(8);
            assertThat(count(jdbc, "warehouses", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_kb_spaces", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_channels", companyId)).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from permission_catalog where code='hr.employee.write.sensitive'")
                .query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp
                join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='HR_MANAGER'
                  and rp.permission_code='hr.employee.write.sensitive'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp
                join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='HR_MANAGER'
                  and rp.permission_code='hr.payroll.read.all'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("select count(*) from permission_catalog where code='pm.workload.write'")
                .query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp
                join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='PROJECT_MANAGER'
                  and rp.permission_code='pm.workload.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from permission_catalog where code='crm.quote.approve'")
                .query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp
                join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='SALES_CRM'
                  and rp.permission_code='crm.customer.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp
                join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='SALES_CRM'
                  and rp.permission_code='crm.quote.approve'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("select count(*) from company_membership_roles where company_id=:companyId")
                .param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("select count(*) from company_membership_scopes where company_id=:companyId")
                .param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            var companyAccess = new CompanyAccess(jdbc);
            var customRoleAuthentication = new TestingAuthenticationToken("dev-user-subject", "unused", "ROLE_USER");
            assertThat(companyAccess.canAccess(customRoleAuthentication, companyId, "company.write")).isTrue();
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='FINANCE_MANAGER' and rp.permission_code='finance.transaction.approve'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='FINANCE_MANAGER' and rp.permission_code='finance.period.close'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='OPERATIONS_MANAGER' and rp.permission_code='inventory.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='OPERATIONS_MANAGER' and rp.permission_code='procurement.approve'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='WORKPLACE_MANAGER' and rp.permission_code='document.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='WORKPLACE_MANAGER' and rp.permission_code='approval.decide'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='EMPLOYEE' and rp.permission_code='platform.search'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='AUTOMATION_MANAGER' and rp.permission_code='automation.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='MANAGER' and rp.permission_code='integration.write'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='EXECUTIVE_MANAGER' and rp.permission_code='reporting.export'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("""
                select count(*) from company_role_permissions rp join company_roles r on r.id=rp.role_id
                where r.company_id=:companyId and r.code='MANAGER' and rp.permission_code='reporting.manage'
                """).param("companyId", companyId.toString()).query(Long.class).single()).isZero();

            var crm = new CrmService(jdbc, new NamedParameterJdbcTemplate(dataSource), new AuditService(jdbc),
                new CrmAccess(companyAccess, jdbc));
            UUID customerId = crm.createCustomer(companyId,
                new CustomerRequest(null, "Công ty thử nghiệm CRM", "ORGANIZATION", "crm@example.test",
                    "0900000000", "TAX-DEMO", null, "Hà Nội", "ENTERPRISE", "REFERRAL", null, "Smoke test"),
                "dev-user-subject").id();
            UUID stageId = UUID.fromString(jdbc.sql("select id from crm_pipeline_stages where company_id=:companyId and code='QUALIFICATION'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID opportunityId = crm.createOpportunity(companyId,
                new OpportunityRequest(null, "Triển khai Company OS", customerId, null, stageId,
                    BigDecimal.valueOf(100000000), "VND", null, LocalDate.now().plusDays(30), null, "Gửi đề xuất"),
                "dev-user-subject").id();
            var item = new LineItemRequest("SVC-01", "Phí triển khai", BigDecimal.ONE,
                BigDecimal.valueOf(100000000), BigDecimal.ZERO);
            UUID templateId = crm.createQuoteTemplate(companyId,
                new QuoteTemplateRequest("Báo giá dịch vụ", "BÁO GIÁ DỊCH VỤ", "DV", "Thanh toán theo tiến độ", "Trân trọng"),
                "dev-user-subject").id();
            UUID quoteId = crm.createQuote(companyId,
                new QuoteRequest(null, customerId, opportunityId, templateId, "VND", BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now().plusDays(14), null, List.of(item)), "dev-user-subject").id();
            var quoteDocument = crm.quoteDocument(companyId, quoteId);
            assertThat(quoteDocument).containsEntry("template_title", "BÁO GIÁ DỊCH VỤ")
                .containsEntry("terms", "Thanh toán theo tiến độ");
            assertThat((List<?>) quoteDocument.get("items")).hasSize(1);
            String quoteNumber = jdbc.sql("select quote_number from crm_quotes where company_id=:companyId and id=:id")
                .param("companyId", companyId.toString()).param("id", quoteId.toString()).query(String.class).single();
            crm.createQuote(companyId,
                new QuoteRequest(quoteNumber, customerId, opportunityId, templateId, "VND", BigDecimal.ZERO, BigDecimal.ZERO,
                    LocalDate.now().plusDays(21), "Phiên bản hai", List.of(item)), "dev-user-subject");
            UUID contractId = crm.createContract(companyId,
                new ContractRequest(null, "Hợp đồng triển khai", customerId, quoteId, LocalDate.now(),
                    LocalDate.now().plusMonths(3), BigDecimal.valueOf(100000000), "VND", "Điều khoản thử nghiệm",
                    null, 30, null), "dev-user-subject").id();
            crm.addContractPayment(companyId, contractId,
                new ContractPaymentRequest("Đợt 1", BigDecimal.valueOf(50000000), LocalDate.now().plusDays(7)),
                "dev-user-subject");
            UUID orderId = crm.createOrder(companyId,
                new SalesOrderRequest(null, customerId, quoteId, contractId, "VND", BigDecimal.ZERO,
                    LocalDate.now(), LocalDate.now().plusDays(15), null, "Smoke test", List.of(item)),
                "dev-user-subject").id();
            crm.createTicket(companyId,
                new TicketRequest(customerId, null, orderId, "Cần hỗ trợ cấu hình", "Kiểm thử ticket",
                    "WEB", "NORMAL", null, null), "dev-user-subject");
            crm.createCampaign(companyId,
                new CampaignRequest(null, "Chiến dịch thử nghiệm", "DIGITAL", "EMAIL", "PLANNED",
                    LocalDate.now(), LocalDate.now().plusDays(30), BigDecimal.valueOf(10000000), BigDecimal.ZERO,
                    "VND", 0, 0, null, "Kiểm thử marketing"), "dev-user-subject");
            assertThat(count(jdbc, "customers", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "crm_opportunities", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "crm_quotes", companyId)).isEqualTo(2);
            assertThat(count(jdbc, "crm_quote_templates", companyId)).isEqualTo(1);
            assertThat(jdbc.sql("select max(version) from crm_quotes where company_id=:companyId and quote_number=:number")
                .param("companyId", companyId.toString()).param("number", quoteNumber).query(Integer.class).single()).isEqualTo(2);
            assertThat(count(jdbc, "crm_contracts", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "crm_sales_orders", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "crm_support_tickets", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "crm_campaigns", companyId)).isEqualTo(1);

            UUID departmentId = UUID.fromString(jdbc.sql("select id from departments where company_id=:companyId and code='TECH'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID employeeId = UUID.randomUUID();
            jdbc.sql("insert into employees(id,company_id,employee_code,keycloak_subject,full_name,department_id,job_title,join_date,employment_status) values(:id,:companyId,'FIN-TEST','finance-user','Finance Test',:department,'Accountant',current_date,'ACTIVE')")
                .param("id", employeeId.toString()).param("companyId", companyId.toString()).param("department", departmentId.toString()).update();
            UUID projectId = UUID.randomUUID();
            jdbc.sql("insert into pm_projects(id,company_id,code,name,department_id,status,currency) values(:id,:companyId,'FIN-PROJ','Finance Test Project',:department,'ACTIVE','VND')")
                .param("id", projectId.toString()).param("companyId", companyId.toString()).param("department", departmentId.toString()).update();
            UUID expenseCategoryId = UUID.fromString(jdbc.sql("select id from finance_categories where company_id=:companyId and code='OFFICE'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            var finance = new FinanceService(jdbc, new NamedParameterJdbcTemplate(dataSource), new AuditService(jdbc),
                new FinanceAccess(companyAccess, jdbc));
            UUID budgetId = finance.createBudget(companyId,
                new BudgetRequest(null, "Ngân sách vận hành", departmentId, null, expenseCategoryId,
                    LocalDate.now().withDayOfMonth(1), LocalDate.now().plusMonths(1), "VND",
                    BigDecimal.valueOf(2000000), "Smoke test"), "dev-user-subject").id();
            finance.decideBudget(companyId, budgetId, new DecisionRequest("ACTIVE", null), "dev-user-subject");
            UUID transactionId = finance.createTransaction(companyId,
                new CashTransactionRequest("PAYMENT", expenseCategoryId, departmentId, null, null, null,
                    BigDecimal.valueOf(1000000), "VND", LocalDate.now(), "BANK", "TX-TEST", "Chi phí kiểm thử", null),
                "dev-user-subject").id();
            finance.decideTransaction(companyId, transactionId, new DecisionRequest("SUBMITTED", null), "dev-user-subject");
            finance.decideTransaction(companyId, transactionId, new DecisionRequest("APPROVED", null), "dev-user-subject");
            finance.decideTransaction(companyId, transactionId, new DecisionRequest("POSTED", null), "dev-user-subject");
            UUID invoiceId = finance.createInvoice(companyId,
                new InvoiceRequest(null, "RECEIVABLE", customerId, null, orderId, contractId, projectId, departmentId,
                    LocalDate.now(), LocalDate.now().plusDays(15), BigDecimal.valueOf(5000000), BigDecimal.valueOf(500000),
                    "VND", "Hóa đơn kiểm thử", null), "dev-user-subject").id();
            finance.addInvoicePayment(companyId, invoiceId,
                new InvoicePaymentRequest(BigDecimal.valueOf(1000000), LocalDate.now(), "BANK", "PAY-TEST", null), "dev-user-subject");
            UUID advanceId = finance.createAdvance(companyId,
                new AdvanceRequest(employeeId, departmentId, projectId, "Công tác", BigDecimal.valueOf(1000000), "VND", LocalDate.now()),
                "dev-user-subject").id();
            finance.decideAdvance(companyId, advanceId, new DecisionRequest("SUBMITTED", null), "dev-user-subject");
            finance.decideAdvance(companyId, advanceId, new DecisionRequest("APPROVED", null), "dev-user-subject");
            finance.decideAdvance(companyId, advanceId, new DecisionRequest("DISBURSED", null), "dev-user-subject");
            finance.addAdvanceExpense(companyId, advanceId,
                new AdvanceExpenseRequest(expenseCategoryId, BigDecimal.valueOf(400000), LocalDate.now(), "Chi phí công tác", null), "dev-user-subject");
            finance.settleAdvance(companyId, advanceId, new AdvanceSettlementRequest(BigDecimal.valueOf(600000)), "dev-user-subject");
            finance.createProjectCost(companyId,
                new ProjectCostRequest(projectId, expenseCategoryId, "SERVICE", BigDecimal.valueOf(200000), "VND", LocalDate.now(), null, null, "Chi phí dự án"),
                "dev-user-subject");
            UUID cashAccountId = UUID.fromString(jdbc.sql("select id from finance_accounts where company_id=:companyId and code='111'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID revenueAccountId = UUID.fromString(jdbc.sql("select id from finance_accounts where company_id=:companyId and code='511'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID journalId = finance.createJournal(companyId,
                new JournalRequest(LocalDate.now(), "Bút toán kiểm thử", "JV-TEST", List.of(
                    new JournalLineRequest(cashAccountId, departmentId, projectId, "Nợ tiền mặt", BigDecimal.valueOf(5000000), BigDecimal.ZERO),
                    new JournalLineRequest(revenueAccountId, departmentId, projectId, "Có doanh thu", BigDecimal.ZERO, BigDecimal.valueOf(5000000)))),
                "dev-user-subject").id();
            finance.postJournal(companyId, journalId, "dev-user-subject");
            finance.setPeriod(companyId, new PeriodRequest(LocalDate.now().getYear(), LocalDate.now().getMonthValue(), "CLOSED"), "dev-user-subject");
            assertThat(count(jdbc, "finance_cash_transactions", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "finance_invoices", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "finance_advances", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "finance_project_costs", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "finance_journals", companyId)).isEqualTo(1);
            assertThat(jdbc.sql("select actual_amount from finance_budgets where id=:id").param("id", budgetId.toString())
                .query(BigDecimal.class).single()).isEqualByComparingTo("1000000");

            var operations = new OperationsService(jdbc, new NamedParameterJdbcTemplate(dataSource), new AuditService(jdbc),
                new OperationsAccess(companyAccess, jdbc));
            UUID supplierId = operations.createSupplier(companyId,
                new SupplierRequest(null, "Nhà cung cấp vận hành", "OPS-TAX", "ops@example.test", "0901000000",
                    "Hà Nội", "Vật tư", 3, "Thanh toán 30 ngày", null, "ACTIVE", "Smoke test"),
                "dev-user-subject").id();
            UUID itemId = operations.createInventoryItem(companyId,
                new InventoryItemRequest("OPS-ITEM", "Vật tư kiểm thử", "MATERIAL", "cái",
                    BigDecimal.ONE, BigDecimal.valueOf(50000), "VND"), "dev-user-subject").id();
            UUID warehouseId = UUID.fromString(jdbc.sql("select id from warehouses where company_id=:companyId and code='MAIN'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID purchaseRequestId = operations.createPurchaseRequest(companyId,
                new PurchaseRequestCreate(departmentId, projectId, budgetId, LocalDate.now().plusDays(5),
                    "Mua vật tư kiểm thử", "VND", List.of(new PurchaseRequestItemRequest(itemId,
                        "Vật tư kiểm thử", BigDecimal.valueOf(2), "cái", BigDecimal.valueOf(50000)))),
                "dev-user-subject").id();
            operations.decidePurchaseRequest(companyId, purchaseRequestId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("SUBMITTED", null), "dev-user-subject");
            operations.decidePurchaseRequest(companyId, purchaseRequestId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("APPROVED", null), "dev-user-subject");
            UUID purchaseOrderId = operations.createPurchaseOrder(companyId,
                new PurchaseOrderRequest(supplierId, purchaseRequestId, warehouseId, LocalDate.now().plusDays(5), "VND",
                    List.of(new PurchaseOrderItemRequest(itemId, "Vật tư kiểm thử", BigDecimal.valueOf(2), "cái", BigDecimal.valueOf(50000)))),
                "dev-user-subject").id();
            operations.decidePurchaseOrder(companyId, purchaseOrderId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("SUBMITTED", null), "dev-user-subject");
            operations.decidePurchaseOrder(companyId, purchaseOrderId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("APPROVED", null), "dev-user-subject");
            UUID purchaseOrderItemId = UUID.fromString(jdbc.sql("select id from ops_purchase_order_items where purchase_order_id=:order")
                .param("order", purchaseOrderId.toString()).query(String.class).single().trim());
            operations.receivePurchaseOrder(companyId, purchaseOrderId,
                new GoodsReceiptRequest(warehouseId, LocalDate.now(), "Nhận đủ",
                    List.of(new ReceiptLineRequest(purchaseOrderItemId, BigDecimal.valueOf(2)))), "dev-user-subject");
            UUID stockCountId = operations.createStockCount(companyId,
                new StockCountRequest(warehouseId, LocalDate.now(), "Kiểm kê",
                    List.of(new StockCountLineRequest(itemId, BigDecimal.valueOf(2)))), "dev-user-subject").id();
            operations.decideStockCount(companyId, stockCountId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("SUBMITTED", null), "dev-user-subject");
            operations.decideStockCount(companyId, stockCountId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("APPROVED", null), "dev-user-subject");
            UUID assetId = operations.createAsset(companyId,
                new AssetRequest(null, "Laptop kiểm thử", "LAPTOP", "SN-OPS", purchaseOrderId, warehouseId,
                    departmentId, LocalDate.now(), BigDecimal.valueOf(20000000), "VND", LocalDate.now().plusYears(1),
                    "GOOD", "Smoke test"), "dev-user-subject").id();
            operations.assignAsset(companyId, assetId,
                new AssetAssignmentRequest(employeeId, LocalDate.now(), "Bàn giao"), "dev-user-subject");
            operations.returnAsset(companyId, assetId,
                new AssetReturnRequest(LocalDate.now(), "GOOD", "Thu hồi"), "dev-user-subject");
            UUID maintenanceId = operations.createMaintenance(companyId,
                new MaintenanceRequest(assetId, supplierId, "INSPECTION", LocalDate.now(), BigDecimal.ZERO,
                    "VND", "Kiểm tra thiết bị"), "dev-user-subject").id();
            operations.updateMaintenance(companyId, maintenanceId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("IN_PROGRESS", null), "dev-user-subject");
            operations.updateMaintenance(companyId, maintenanceId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("COMPLETED", "Đạt"), "dev-user-subject");
            operations.createLicense(companyId,
                new LicenseRequest("LIC-OPS", "Phần mềm kiểm thử", supplierId, employeeId, 10, 1,
                    LocalDate.now(), LocalDate.now().plusYears(1), "YEARLY", BigDecimal.valueOf(1000000), "VND", "vault://license"),
                "dev-user-subject");
            UUID manufacturingId = operations.createManufacturingOrder(companyId,
                new ManufacturingRequest(itemId, warehouseId, BigDecimal.valueOf(3), LocalDate.now(), LocalDate.now().plusDays(2), "Smoke test"),
                "dev-user-subject").id();
            operations.updateManufacturingOrder(companyId, manufacturingId,
                new ManufacturingProgressRequest("RELEASED", BigDecimal.ZERO, null), "dev-user-subject");
            operations.updateManufacturingOrder(companyId, manufacturingId,
                new ManufacturingProgressRequest("IN_PROGRESS", BigDecimal.ZERO, null), "dev-user-subject");
            operations.updateManufacturingOrder(companyId, manufacturingId,
                new ManufacturingProgressRequest("COMPLETED", BigDecimal.valueOf(3), null), "dev-user-subject");
            UUID shipmentId = operations.createShipment(companyId,
                new ShipmentRequest("OUTBOUND", null, orderId, warehouseId, "Hà Nội", "Demo Carrier", "TRACK-OPS",
                    LocalDate.now().plusDays(2), "Smoke test"), "dev-user-subject").id();
            operations.updateShipment(companyId, shipmentId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("READY", null), "dev-user-subject");
            operations.updateShipment(companyId, shipmentId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("IN_TRANSIT", null), "dev-user-subject");
            operations.updateShipment(companyId, shipmentId,
                new com.company.erp.operations.OperationsModels.DecisionRequest("DELIVERED", null), "dev-user-subject");
            assertThat(count(jdbc, "ops_goods_receipts", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "ops_assets", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "ops_software_licenses", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "ops_manufacturing_orders", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "ops_shipments", companyId)).isEqualTo(1);
            assertThat(jdbc.sql("select on_hand from ops_stock_levels where company_id=:companyId and warehouse_id=:warehouse and inventory_item_id=:item")
                .param("companyId", companyId.toString()).param("warehouse", warehouseId.toString()).param("item", itemId.toString())
                .query(BigDecimal.class).single()).isEqualByComparingTo("5");
            assertThat(jdbc.sql("select actual_amount from finance_budgets where id=:id").param("id", budgetId.toString())
                .query(BigDecimal.class).single()).isEqualByComparingTo("1100000");

            var workplace = new WorkplaceService(jdbc, new NamedParameterJdbcTemplate(dataSource), new AuditService(jdbc),
                new WorkplaceAccess(companyAccess, jdbc), new ObjectMapper());
            UUID documentId = workplace.createDocument(companyId,
                new DocumentRequest(null, "Quy trình thử nghiệm", "POLICY", "Smoke test", "COMPANY", null),
                "dev-user-subject").id();
            UUID versionId = workplace.addDocumentVersion(companyId, documentId,
                new DocumentVersionRequest("policy.pdf", "workplace/demo/policy.pdf", "application/pdf", 128L,
                    "0".repeat(64), "Phiên bản đầu"), "dev-user-subject").id();
            workplace.markVersionScan(companyId, versionId, new ScanStatusRequest("CLEAN"), "dev-user-subject");
            workplace.setDocumentStatus(companyId, documentId, new StatusRequest("PUBLISHED", null), "dev-user-subject");
            UUID spaceId = UUID.fromString(jdbc.sql("select id from workplace_kb_spaces where company_id=:companyId and code='GENERAL'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            UUID articleId = workplace.createKnowledgeArticle(companyId,
                new KnowledgeArticleRequest(spaceId, "huong-dan-thu-nghiem", "Hướng dẫn thử nghiệm", "Smoke test", "Nội dung hướng dẫn"),
                "dev-user-subject").id();
            workplace.setKnowledgeArticleStatus(companyId, articleId, new StatusRequest("PUBLISHED", null), "dev-user-subject");
            UUID approvalId = workplace.createApproval(companyId,
                new ApprovalRequest("GENERAL", "Phê duyệt thử nghiệm", "Smoke test", "DOCUMENT", documentId,
                    departmentId, projectId, BigDecimal.valueOf(100000), "VND", List.of(new ApproverRequest("dev-user-subject"))),
                "dev-user-subject").id();
            workplace.submitApproval(companyId, approvalId, "dev-user-subject");
            workplace.decideApproval(companyId, approvalId, new StatusRequest("APPROVED", "Đạt"), "dev-user-subject");
            UUID formId = workplace.createForm(companyId,
                new FormDefinitionRequest("SMOKE", "Biểu mẫu thử nghiệm", "Smoke test",
                    "{\"properties\":{\"content\":{\"type\":\"string\"}},\"required\":[\"content\"]}", false), "dev-user-subject").id();
            workplace.setFormStatus(companyId, formId, new StatusRequest("PUBLISHED", null), "dev-user-subject");
            workplace.submitForm(companyId, formId, new FormSubmissionRequest("{\"content\":\"OK\"}", List.of()), "dev-user-subject");
            UUID signatureId = workplace.createSignature(companyId,
                new SignatureRequest(documentId, "Xác nhận quy trình", "Smoke test", LocalDateTime.now().plusDays(7),
                    List.of("dev-user-subject")), "dev-user-subject").id();
            workplace.sendSignature(companyId, signatureId, "dev-user-subject");
            workplace.decideSignature(companyId, signatureId, new SignatureDecisionRequest("SIGNED", "Đã đọc"), "dev-user-subject");
            UUID ticketId = workplace.createTicket(companyId,
                new HelpdeskTicketRequest("IT", "Yêu cầu thử nghiệm", "Kiểm tra helpdesk", "NORMAL", departmentId),
                "dev-user-subject").id();
            workplace.addTicketComment(companyId, ticketId, new HelpdeskCommentRequest("Đã tiếp nhận", false),
                "dev-user-subject", false);
            UUID channelId = UUID.fromString(jdbc.sql("select id from workplace_channels where company_id=:companyId and code='GENERAL'")
                .param("companyId", companyId.toString()).query(String.class).single().trim());
            workplace.createMessage(companyId, channelId, new MessageRequest(null, "Tin nhắn thử nghiệm"), "dev-user-subject");
            UUID legalId = workplace.createLegalRecord(companyId,
                new LegalRecordRequest(null, "CONTRACT", "Hợp đồng thử nghiệm", "Đối tác A", LocalDate.now(),
                    LocalDate.now().plusMonths(1), "dev-user-subject", documentId, "Smoke test"), "dev-user-subject").id();
            UUID obligationId = workplace.createObligation(companyId, legalId,
                new LegalObligationRequest("Hoàn tất hồ sơ", LocalDate.now().plusDays(5), null, "dev-user-subject", 3),
                "dev-user-subject").id();
            workplace.setObligationStatus(companyId, obligationId, new StatusRequest("COMPLETED", null), "dev-user-subject");
            assertThat(count(jdbc, "workplace_documents", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_approval_requests", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_form_submissions", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_signature_requests", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_helpdesk_tickets", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "workplace_legal_records", companyId)).isEqualTo(1);
            var productivity = new ProductivityService(jdbc, new NamedParameterJdbcTemplate(dataSource), companyAccess,
                new com.company.erp.pm.PmAccess(companyAccess, jdbc), new CrmAccess(companyAccess, jdbc),
                new WorkplaceAccess(companyAccess, jdbc), new AuditService(jdbc), new ObjectMapper());
            UUID notificationId = productivity.createNotification(companyId,
                new NotificationRequest(null, "Nhắc việc thử nghiệm", "Smoke test", "GENERAL", "INFO",
                    "/productivity", null, null, "smoke-reminder"), customRoleAuthentication).id();
            productivity.markNotification(companyId, notificationId, "READ", customRoleAuthentication);
            productivity.createSavedView(companyId,
                new SavedViewRequest("pm.tasks", "Task đang mở", "{\"status\":\"OPEN\"}", false, true),
                customRoleAuthentication);
            assertThat(count(jdbc, "platform_notifications", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "platform_saved_views", companyId)).isEqualTo(1);
            assertThat(productivity.search(companyId, "Quy trình", 20, customRoleAuthentication)).isNotEmpty();

            var automation = new AutomationService(jdbc, new NamedParameterJdbcTemplate(dataSource),
                new AuditService(jdbc), workplace, new ObjectMapper());
            UUID connectionId = automation.createConnection(companyId,
                new ConnectionRequest("DEMO", "Kết nối thử nghiệm", "CUSTOM", null, "vault://demo", "{}"),
                "dev-user-subject").id();
            automation.setConnectionStatus(companyId, connectionId,
                new com.company.erp.automation.AutomationModels.StatusRequest("ACTIVE"), "dev-user-subject");
            UUID workflowId = automation.createWorkflow(companyId,
                new WorkflowRequest("SMOKE", "Workflow thử nghiệm", "Smoke test", "MANUAL", "{}", List.of(
                    new WorkflowStepRequest("Thông báo", "NOTIFICATION",
                        "{\"targetSubject\":\"dev-user-subject\",\"title\":\"Workflow hoàn tất\"}", false))),
                "dev-user-subject").id();
            automation.setWorkflowStatus(companyId, workflowId,
                new com.company.erp.automation.AutomationModels.StatusRequest("ACTIVE"), "dev-user-subject");
            automation.run(companyId, workflowId,
                new com.company.erp.automation.AutomationModels.RunRequest("{\"source\":\"smoke\"}"), "dev-user-subject");
            assertThat(count(jdbc, "integration_connections", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "automation_workflows", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "automation_runs", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "platform_notifications", companyId)).isEqualTo(2);

            var reporting = new ReportingService(jdbc, new NamedParameterJdbcTemplate(dataSource),
                new ReportingAccess(companyAccess), new AuditService(jdbc), new ObjectMapper());
            UUID objectiveId = reporting.createObjective(companyId,
                new ObjectiveRequest("OBJ-SMOKE", "Mục tiêu thử nghiệm", "Smoke test", "COMPANY", companyId,
                    "dev-user-subject", LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31),
                    List.of(new KeyResultRequest("KR-1", "Hoàn tất mục tiêu", "PERCENT",
                        BigDecimal.ZERO, BigDecimal.valueOf(100), BigDecimal.valueOf(25), "%", BigDecimal.ONE))),
                customRoleAuthentication).id();
            UUID keyResultId = UUID.fromString(jdbc.sql("select id from strategic_key_results where objective_id=:id")
                .param("id", objectiveId.toString()).query(String.class).single().trim());
            reporting.updateProgress(companyId, keyResultId,
                new ProgressRequest(BigDecimal.valueOf(60), "ON_TRACK"), customRoleAuthentication);
            reporting.createSnapshot(companyId,
                new SnapshotRequest("Báo cáo smoke", "EXECUTIVE", LocalDate.now().withDayOfMonth(1), LocalDate.now()),
                customRoleAuthentication);
            assertThat(count(jdbc, "strategic_objectives", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "strategic_key_results", companyId)).isEqualTo(1);
            assertThat(count(jdbc, "management_report_snapshots", companyId)).isEqualTo(1);

            jdbc.sql("update company_memberships set status='DISABLED' where company_id=:companyId")
                .param("companyId", companyId.toString()).update();
            assertThat(companyAccess.canAccess(customRoleAuthentication, companyId, "company.write")).isFalse();
        }
    }

    private long count(JdbcClient jdbc, String table, UUID companyId) {
        return jdbc.sql("select count(*) from " + table + " where company_id=:companyId")
            .param("companyId", companyId.toString()).query(Long.class).single();
    }
}
