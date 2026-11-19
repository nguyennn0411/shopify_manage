package com.company.erp.company;

import java.util.UUID;
import java.sql.Types;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyReferenceDataProvisioner {
    private final JdbcClient jdbc;

    public CompanyReferenceDataProvisioner(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void provision(UUID companyId) {
        provisionLeaveType(companyId, "ANNUAL", "Phép năm", true, 12, false);
        provisionLeaveType(companyId, "SICK", "Nghỉ ốm", true, 30, true);
        provisionLeaveType(companyId, "UNPAID", "Nghỉ không lương", false, null, false);
        jdbc.sql("""
            insert into work_shifts(id,company_id,code,name,start_time,end_time,break_minutes,late_grace_minutes,active)
            values(:id,:companyId,'OFFICE','Ca hành chính','08:30:00','17:30:00',60,10,true)
            on conflict(company_id,code) do nothing
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString()).update();

        provisionStatus(companyId, "TASK", "BACKLOG", "Backlog", 0, "#6b7f8a", false);
        provisionStatus(companyId, "TASK", "TODO", "Đến làm", 1, "#5b8def", false);
        provisionStatus(companyId, "TASK", "IN_PROGRESS", "Đang thực hiện", 2, "#e0a52c", false);
        provisionStatus(companyId, "TASK", "IN_REVIEW", "Đang xem xét", 3, "#9b6bd6", false);
        provisionStatus(companyId, "TASK", "DONE", "Hoàn thành", 4, "#43a477", true);
        provisionStatus(companyId, "ISSUE", "OPEN", "Mở", 0, "#b23b32", false);
        provisionStatus(companyId, "ISSUE", "IN_PROGRESS", "Đang xử lý", 1, "#e0a52c", false);
        provisionStatus(companyId, "ISSUE", "RESOLVED", "Đã khắc phục", 2, "#43a477", true);
        provisionStatus(companyId, "ISSUE", "CLOSED", "Đóng", 3, "#6b7f8a", true);

        provisionCrmStage(companyId, "QUALIFICATION", "Tiếp nhận & đánh giá", 10, 0, false, false);
        provisionCrmStage(companyId, "DISCOVERY", "Khảo sát nhu cầu", 25, 1, false, false);
        provisionCrmStage(companyId, "PROPOSAL", "Đề xuất giải pháp", 50, 2, false, false);
        provisionCrmStage(companyId, "NEGOTIATION", "Đàm phán", 75, 3, false, false);
        provisionCrmStage(companyId, "WON", "Thành công", 100, 4, true, false);
        provisionCrmStage(companyId, "LOST", "Thất bại", 0, 5, false, true);

        provisionFinanceCategory(companyId, "SALES_REVENUE", "Doanh thu bán hàng", "INCOME");
        provisionFinanceCategory(companyId, "SERVICE_REVENUE", "Doanh thu dịch vụ", "INCOME");
        provisionFinanceCategory(companyId, "OTHER_INCOME", "Thu nhập khác", "INCOME");
        provisionFinanceCategory(companyId, "PAYROLL", "Chi phí nhân sự", "EXPENSE");
        provisionFinanceCategory(companyId, "SOFTWARE", "Phần mềm & dịch vụ", "EXPENSE");
        provisionFinanceCategory(companyId, "OFFICE", "Văn phòng", "EXPENSE");
        provisionFinanceCategory(companyId, "MARKETING", "Marketing", "EXPENSE");
        provisionFinanceCategory(companyId, "TRAVEL", "Công tác", "EXPENSE");
        provisionFinanceCategory(companyId, "OTHER_EXPENSE", "Chi phí khác", "EXPENSE");
        provisionFinanceAccount(companyId, "111", "Tiền mặt", "ASSET");
        provisionFinanceAccount(companyId, "112", "Tiền gửi ngân hàng", "ASSET");
        provisionFinanceAccount(companyId, "131", "Phải thu khách hàng", "ASSET");
        provisionFinanceAccount(companyId, "141", "Tạm ứng", "ASSET");
        provisionFinanceAccount(companyId, "331", "Phải trả nhà cung cấp", "LIABILITY");
        provisionFinanceAccount(companyId, "411", "Vốn chủ sở hữu", "EQUITY");
        provisionFinanceAccount(companyId, "511", "Doanh thu", "REVENUE");
        provisionFinanceAccount(companyId, "642", "Chi phí quản lý", "EXPENSE");

        provisionWarehouse(companyId, "MAIN", "Kho chính", null);
        provisionKnowledgeSpace(companyId, "GENERAL", "Tri thức chung", "Quy định, hướng dẫn và SOP dùng chung");
        provisionChannel(companyId, "GENERAL", "Trao đổi chung", "Kênh trao đổi toàn công ty");

        provisionRole(companyId, "COMPANY_ADMIN", "Quản trị công ty", "Toàn quyền cấu hình trong công ty",
            jdbc.sql("select code from permission_catalog order by code").query(String.class).list().toArray(String[]::new));
        provisionRole(companyId, "MANAGER", "Quản lý", "Quản lý trong phạm vi được cấp",
            new String[] { "company.read", "hr.access", "hr.employee.read.basic", "pm.access",
                "pm.project.read", "pm.project.write", "pm.task.read", "pm.task.write",
                "pm.sprint.read", "pm.sprint.write", "pm.timesheet.read", "pm.timesheet.approve",
                "pm.workload.read", "pm.workload.write", "pm.report.read", "pm.issue.read", "pm.issue.write",
                "workplace.access", "document.read", "knowledge.read", "approval.read", "approval.write",
                "approval.decide", "form.read", "form.submit", "signature.read", "signature.sign",
                "helpdesk.read", "helpdesk.write", "communication.read", "communication.write",
                "platform.search", "notification.read", "notification.write", "saved-view.read", "saved-view.write",
                "automation.access", "automation.read", "automation.run", "reporting.access", "reporting.read",
                "strategy.read", "strategy.write" });
        provisionRole(companyId, "EMPLOYEE", "Nhân viên", "Cổng nhân viên và công việc được chia sẻ",
            new String[] { "company.read", "hr.access", "pm.access", "pm.project.read", "pm.task.read", "pm.task.write",
                "pm.sprint.read", "pm.timesheet.read.own", "pm.timesheet.write", "pm.issue.read", "pm.issue.write",
                "workplace.access", "document.read", "knowledge.read", "approval.read", "approval.write",
                "form.read", "form.submit", "signature.read", "signature.sign", "helpdesk.read", "helpdesk.write",
                "communication.read", "communication.write", "platform.search", "notification.read",
                "notification.write", "saved-view.read", "saved-view.write" });
        provisionRole(companyId, "AUDITOR", "Kiểm toán viên", "Quyền đọc được ủy quyền",
            new String[] { "company.read", "audit.read", "platform.search", "notification.read", "saved-view.read",
                "reporting.access", "reporting.read", "strategy.read" });
        provisionRole(companyId, "HR_MANAGER", "Quản lý nhân sự",
            "Quản lý vòng đời nhân viên; không mặc định xem hoặc vận hành bảng lương",
            new String[] { "company.read", "hr.access", "hr.employee.read.basic", "hr.employee.read.sensitive",
                "hr.employee.write", "hr.employee.write.sensitive", "hr.recruitment.read", "hr.recruitment.write",
                "hr.onboarding.read", "hr.onboarding.write", "hr.attendance.read", "hr.attendance.write",
                "hr.attendance.approve", "hr.leave.read", "hr.leave.write", "hr.leave.approve",
                "hr.performance.read", "hr.performance.write", "hr.training.read", "hr.training.write",
                "hr.offboarding.read", "hr.offboarding.write" });
        provisionRole(companyId, "PAYROLL_MANAGER", "Quản lý tiền lương",
            "Quản lý cấu trúc lương, kỳ lương và phiếu lương",
            new String[] { "company.read", "hr.access", "hr.employee.read.basic", "hr.payroll.read.all",
                "hr.payroll.write" });
        provisionRole(companyId, "PROJECT_MANAGER", "Quản lý dự án",
            "Quản lý dự án, nguồn lực, timesheet và sự cố trong phạm vi được giao",
            new String[] { "company.read", "hr.employee.read.basic", "pm.access", "pm.project.read", "pm.project.write", "pm.task.read",
                "pm.task.write", "pm.sprint.read", "pm.sprint.write", "pm.timesheet.read", "pm.timesheet.write",
                "pm.timesheet.approve", "pm.workload.read", "pm.workload.write", "pm.report.read",
                "pm.issue.read", "pm.issue.write" });
        provisionRole(companyId, "SALES_CRM", "Kinh doanh & CRM",
            "Quản lý khách hàng, pipeline, báo giá, hợp đồng, đơn bán và chăm sóc khách hàng trong phạm vi được giao",
            new String[] { "company.read", "crm.access", "crm.customer.read", "crm.customer.write",
                "crm.pipeline.read", "crm.pipeline.write", "crm.quote.read", "crm.quote.write",
                "crm.contract.read", "crm.contract.write", "crm.order.read", "crm.order.write",
                "crm.support.read", "crm.support.write", "crm.marketing.read", "crm.marketing.write" });
        provisionRole(companyId, "FINANCE_MANAGER", "Tài chính & Kế toán quản trị",
            "Quản lý thu chi, ngân sách, công nợ, hóa đơn, tạm ứng, chi phí dự án và sổ kế toán trong phạm vi được giao",
            new String[] { "company.read", "finance.access", "finance.transaction.read", "finance.transaction.write",
                "finance.transaction.approve", "finance.budget.read", "finance.budget.write", "finance.invoice.read",
                "finance.invoice.write", "finance.advance.read", "finance.advance.write", "finance.advance.approve",
                "finance.project-cost.read", "finance.project-cost.write", "finance.accounting.read",
                "finance.accounting.write", "finance.report.read" });
        provisionRole(companyId, "OPERATIONS_MANAGER", "Quản lý vận hành",
            "Quản lý nhà cung cấp, mua sắm, kho, tài sản, bảo trì, giấy phép và giao nhận; quyền phê duyệt được cấp riêng",
            new String[] { "company.read", "operations.access", "supplier.read", "supplier.write",
                "procurement.read", "procurement.write", "inventory.read", "inventory.write",
                "asset.read", "asset.write", "maintenance.read", "maintenance.write",
                "license.read", "license.write", "manufacturing.read", "manufacturing.write",
                "fulfillment.read", "fulfillment.write" });
        provisionRole(companyId, "WORKPLACE_MANAGER", "Quản lý không gian làm việc",
            "Quản lý tài liệu, tri thức, biểu mẫu, helpdesk, truyền thông và hồ sơ pháp lý; quyền phê duyệt được cấp riêng",
            new String[] { "company.read", "workplace.access", "document.read", "document.write", "document.share",
                "knowledge.read", "knowledge.write", "approval.read", "approval.write", "form.read", "form.write",
                "form.submit", "signature.read", "signature.write", "signature.sign", "helpdesk.read", "helpdesk.write",
                "helpdesk.manage", "communication.read", "communication.write", "communication.moderate",
                "legal.read", "legal.write" });
        provisionRole(companyId, "AUTOMATION_MANAGER", "Quản lý tự động hóa & tích hợp",
            "Thiết kế workflow, quản lý kết nối và webhook; không lưu bí mật tích hợp trực tiếp",
            new String[] { "company.read", "automation.access", "automation.read", "automation.write", "automation.run",
                "integration.read", "integration.write", "webhook.read", "webhook.write", "notification.manage",
                "approval.write" });
        provisionRole(companyId, "EXECUTIVE_MANAGER", "Điều hành & Báo cáo",
            "Xem KPI toàn công ty, quản lý mục tiêu chiến lược và snapshot báo cáo",
            new String[] { "company.read", "reporting.access", "reporting.read", "reporting.manage",
                "reporting.export", "strategy.read", "strategy.write" });
    }

    private void provisionLeaveType(UUID companyId, String code, String name, boolean paid,
            Integer allowance, boolean requiresDocument) {
        jdbc.sql("""
            insert into leave_types(id,company_id,code,name,paid,annual_allowance,requires_document,active)
            values(:id,:companyId,:code,:name,:paid,:allowance,:requiresDocument,true)
            on conflict(company_id,code) do nothing
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("paid", paid)
            .param("allowance", allowance, Types.INTEGER).param("requiresDocument", requiresDocument).update();
    }

    private void provisionStatus(UUID companyId, String type, String code, String name,
            int order, String color, boolean done) {
        jdbc.sql("""
            insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
            values(:id,:companyId,:type,:code,:name,:sortOrder,:color,:done,true)
            on conflict(company_id,workflow_type,code) do nothing
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("type", type).param("code", code).param("name", name).param("sortOrder", order)
            .param("color", color).param("done", done).update();
    }

    private void provisionCrmStage(UUID companyId, String code, String name, int probability,
            int order, boolean won, boolean lost) {
        jdbc.sql("""
            insert into crm_pipeline_stages(id,company_id,code,name,probability,sort_order,is_won,is_lost,active)
            values(:id,:companyId,:code,:name,:probability,:sortOrder,:won,:lost,true)
            on conflict(company_id,code) do update set name=excluded.name,probability=excluded.probability,
              sort_order=excluded.sort_order,is_won=excluded.is_won,is_lost=excluded.is_lost,active=true
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("probability", probability)
            .param("sortOrder", order).param("won", won).param("lost", lost).update();
    }

    private void provisionFinanceCategory(UUID companyId, String code, String name, String type) {
        jdbc.sql("""
            insert into finance_categories(id,company_id,code,name,category_type,active)
            values(:id,:companyId,:code,:name,:type,true)
            on conflict(company_id,code) do update set name=excluded.name,category_type=excluded.category_type,active=true
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("type", type).update();
    }

    private void provisionFinanceAccount(UUID companyId, String code, String name, String type) {
        jdbc.sql("""
            insert into finance_accounts(id,company_id,code,name,account_type,active)
            values(:id,:companyId,:code,:name,:type,true)
            on conflict(company_id,code) do update set name=excluded.name,account_type=excluded.account_type,active=true
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("type", type).update();
    }

    private void provisionWarehouse(UUID companyId, String code, String name, String address) {
        jdbc.sql("""
            insert into warehouses(id,company_id,code,name,address,status)
            values(:id,:companyId,:code,:name,:address,'ACTIVE')
            on conflict(company_id,code) do update set name=excluded.name,status='ACTIVE'
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("address", address, Types.VARCHAR).update();
    }

    private void provisionKnowledgeSpace(UUID companyId, String code, String name, String description) {
        jdbc.sql("""
            insert into workplace_kb_spaces(id,company_id,code,name,description,scope_type,owner_subject)
            values(:id,:companyId,:code,:name,:description,'COMPANY','system')
            on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,archived=false
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("description", description).update();
    }

    private void provisionChannel(UUID companyId, String code, String name, String description) {
        jdbc.sql("""
            insert into workplace_channels(id,company_id,code,name,channel_type,description,created_by_subject)
            values(:id,:companyId,:code,:name,'COMPANY',:description,'system')
            on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,archived=false
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("description", description).update();
    }

    private void provisionRole(UUID companyId, String code, String name, String description, String[] permissions) {
        jdbc.sql("""
            insert into company_roles(id,company_id,code,name,description,system_role,active)
            values(:id,:companyId,:code,:name,:description,true,true)
            on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true
            """).param("id", UUID.randomUUID().toString()).param("companyId", companyId.toString())
            .param("code", code).param("name", name).param("description", description).update();
        String roleId = jdbc.sql("select id from company_roles where company_id=:companyId and code=:code")
            .param("companyId", companyId.toString()).param("code", code).query(String.class).single().trim();
        for (String permission : permissions) {
            jdbc.sql("""
                insert into company_role_permissions(role_id,permission_code) values(:roleId,:permission)
                on conflict(role_id,permission_code) do nothing
                """).param("roleId", roleId).param("permission", permission).update();
        }
    }
}
