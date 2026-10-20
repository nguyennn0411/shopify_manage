CREATE TYPE "EmployeeStatus" AS ENUM ('ACTIVE', 'ON_LEAVE', 'TERMINATED');
CREATE TYPE "EmploymentType" AS ENUM ('FULL_TIME', 'PART_TIME', 'CONTRACTOR', 'INTERN');
CREATE TYPE "AttendanceStatus" AS ENUM ('PRESENT', 'REMOTE', 'ABSENT', 'LEAVE');
CREATE TYPE "LeaveRequestStatus" AS ENUM ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED');
CREATE TYPE "WorkTaskStatus" AS ENUM ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED');
CREATE TYPE "WorkTaskPriority" AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'URGENT');
CREATE TYPE "KpiPeriod" AS ENUM ('MONTHLY', 'QUARTERLY', 'ANNUAL');
CREATE TYPE "PayrollRunStatus" AS ENUM ('DRAFT', 'APPROVED', 'EXPORTED');

CREATE TABLE "departments" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "code" TEXT NOT NULL, "name" TEXT NOT NULL,
  "description" TEXT, "active" BOOLEAN NOT NULL DEFAULT true, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL, CONSTRAINT "departments_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "employees" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "department_id" UUID, "user_id" UUID, "manager_id" UUID,
  "employee_number" TEXT NOT NULL, "full_name" TEXT NOT NULL, "work_email" TEXT, "phone" TEXT, "job_title" TEXT NOT NULL,
  "employment_type" "EmploymentType" NOT NULL DEFAULT 'FULL_TIME', "status" "EmployeeStatus" NOT NULL DEFAULT 'ACTIVE',
  "start_date" DATE NOT NULL, "end_date" DATE, "base_salary" DECIMAL(18,4), "salary_currency" TEXT,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "employees_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "attendances" (
  "id" UUID NOT NULL, "employee_id" UUID NOT NULL, "work_date" DATE NOT NULL, "check_in_at" TIMESTAMP(3), "check_out_at" TIMESTAMP(3),
  "status" "AttendanceStatus" NOT NULL DEFAULT 'PRESENT', "note" TEXT, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updated_at" TIMESTAMP(3) NOT NULL, CONSTRAINT "attendances_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "leave_requests" (
  "id" UUID NOT NULL, "employee_id" UUID NOT NULL, "start_date" DATE NOT NULL, "end_date" DATE NOT NULL, "leave_type" TEXT NOT NULL,
  "reason" TEXT, "status" "LeaveRequestStatus" NOT NULL DEFAULT 'DRAFT', "approver_id" UUID, "approved_at" TIMESTAMP(3),
  "decision_note" TEXT, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "leave_requests_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "work_tasks" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "title" TEXT NOT NULL, "description" TEXT,
  "status" "WorkTaskStatus" NOT NULL DEFAULT 'TODO', "priority" "WorkTaskPriority" NOT NULL DEFAULT 'MEDIUM',
  "due_at" TIMESTAMP(3), "completed_at" TIMESTAMP(3), "created_by_id" UUID NOT NULL,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "work_tasks_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "work_task_assignments" (
  "task_id" UUID NOT NULL, "employee_id" UUID NOT NULL, "assigned_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "work_task_assignments_pkey" PRIMARY KEY ("task_id", "employee_id")
);
CREATE TABLE "kpi_definitions" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "department_id" UUID, "code" TEXT NOT NULL, "name" TEXT NOT NULL,
  "unit" TEXT NOT NULL, "period" "KpiPeriod" NOT NULL, "weight" DECIMAL(7,4) NOT NULL, "active" BOOLEAN NOT NULL DEFAULT true,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "kpi_definitions_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "kpi_entries" (
  "id" UUID NOT NULL, "definition_id" UUID NOT NULL, "employee_id" UUID NOT NULL, "period_start" DATE NOT NULL, "period_end" DATE NOT NULL,
  "target_value" DECIMAL(18,4) NOT NULL, "actual_value" DECIMAL(18,4), "score" DECIMAL(9,4), "note" TEXT,
  "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, "updated_at" TIMESTAMP(3) NOT NULL,
  CONSTRAINT "kpi_entries_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "payroll_runs" (
  "id" UUID NOT NULL, "company_id" UUID NOT NULL, "period_start" DATE NOT NULL, "period_end" DATE NOT NULL, "currency" TEXT NOT NULL,
  "status" "PayrollRunStatus" NOT NULL DEFAULT 'DRAFT', "approved_by_id" UUID, "approved_at" TIMESTAMP(3), "exported_at" TIMESTAMP(3),
  "created_by_id" UUID NOT NULL, "created_at" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT "payroll_runs_pkey" PRIMARY KEY ("id")
);
CREATE TABLE "payroll_lines" (
  "id" UUID NOT NULL, "payroll_run_id" UUID NOT NULL, "employee_id" UUID NOT NULL, "base_salary" DECIMAL(18,4) NOT NULL,
  "allowances" DECIMAL(18,4) NOT NULL DEFAULT 0, "deductions" DECIMAL(18,4) NOT NULL DEFAULT 0, "net_pay" DECIMAL(18,4) NOT NULL,
  CONSTRAINT "payroll_lines_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "departments_company_id_code_key" ON "departments"("company_id", "code");
CREATE INDEX "departments_company_id_name_idx" ON "departments"("company_id", "name");
CREATE UNIQUE INDEX "employees_user_id_key" ON "employees"("user_id");
CREATE UNIQUE INDEX "employees_company_id_employee_number_key" ON "employees"("company_id", "employee_number");
CREATE INDEX "employees_company_id_status_idx" ON "employees"("company_id", "status");
CREATE INDEX "employees_department_id_idx" ON "employees"("department_id");
CREATE UNIQUE INDEX "attendances_employee_id_work_date_key" ON "attendances"("employee_id", "work_date");
CREATE INDEX "attendances_work_date_status_idx" ON "attendances"("work_date", "status");
CREATE INDEX "leave_requests_employee_id_start_date_idx" ON "leave_requests"("employee_id", "start_date");
CREATE INDEX "leave_requests_status_start_date_idx" ON "leave_requests"("status", "start_date");
CREATE INDEX "work_tasks_company_id_status_due_at_idx" ON "work_tasks"("company_id", "status", "due_at");
CREATE UNIQUE INDEX "kpi_definitions_company_id_code_key" ON "kpi_definitions"("company_id", "code");
CREATE UNIQUE INDEX "kpi_entries_definition_id_employee_id_period_start_key" ON "kpi_entries"("definition_id", "employee_id", "period_start");
CREATE INDEX "kpi_entries_employee_id_period_start_idx" ON "kpi_entries"("employee_id", "period_start");
CREATE UNIQUE INDEX "payroll_runs_company_id_period_start_period_end_currency_key" ON "payroll_runs"("company_id", "period_start", "period_end", "currency");
CREATE INDEX "payroll_runs_company_id_status_idx" ON "payroll_runs"("company_id", "status");
CREATE UNIQUE INDEX "payroll_lines_payroll_run_id_employee_id_key" ON "payroll_lines"("payroll_run_id", "employee_id");

ALTER TABLE "departments" ADD CONSTRAINT "departments_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "employees" ADD CONSTRAINT "employees_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "employees" ADD CONSTRAINT "employees_department_id_fkey" FOREIGN KEY ("department_id") REFERENCES "departments"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "employees" ADD CONSTRAINT "employees_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "users"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "employees" ADD CONSTRAINT "employees_manager_id_fkey" FOREIGN KEY ("manager_id") REFERENCES "employees"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "attendances" ADD CONSTRAINT "attendances_employee_id_fkey" FOREIGN KEY ("employee_id") REFERENCES "employees"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "leave_requests" ADD CONSTRAINT "leave_requests_employee_id_fkey" FOREIGN KEY ("employee_id") REFERENCES "employees"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "work_tasks" ADD CONSTRAINT "work_tasks_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "work_task_assignments" ADD CONSTRAINT "work_task_assignments_task_id_fkey" FOREIGN KEY ("task_id") REFERENCES "work_tasks"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "work_task_assignments" ADD CONSTRAINT "work_task_assignments_employee_id_fkey" FOREIGN KEY ("employee_id") REFERENCES "employees"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "kpi_definitions" ADD CONSTRAINT "kpi_definitions_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "kpi_definitions" ADD CONSTRAINT "kpi_definitions_department_id_fkey" FOREIGN KEY ("department_id") REFERENCES "departments"("id") ON DELETE SET NULL ON UPDATE CASCADE;
ALTER TABLE "kpi_entries" ADD CONSTRAINT "kpi_entries_definition_id_fkey" FOREIGN KEY ("definition_id") REFERENCES "kpi_definitions"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "kpi_entries" ADD CONSTRAINT "kpi_entries_employee_id_fkey" FOREIGN KEY ("employee_id") REFERENCES "employees"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "payroll_runs" ADD CONSTRAINT "payroll_runs_company_id_fkey" FOREIGN KEY ("company_id") REFERENCES "companies"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "payroll_lines" ADD CONSTRAINT "payroll_lines_payroll_run_id_fkey" FOREIGN KEY ("payroll_run_id") REFERENCES "payroll_runs"("id") ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE "payroll_lines" ADD CONSTRAINT "payroll_lines_employee_id_fkey" FOREIGN KEY ("employee_id") REFERENCES "employees"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
