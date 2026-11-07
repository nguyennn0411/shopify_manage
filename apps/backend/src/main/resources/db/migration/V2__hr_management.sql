create table employees (
  id char(36) primary key, company_id char(36) not null, employee_code varchar(40) not null,
  keycloak_subject varchar(100) null, full_name varchar(180) not null, work_email varchar(254) null,
  department_id char(36) null, job_title varchar(180) not null, manager_employee_id char(36) null,
  join_date date not null, employment_status varchar(30) not null default 'ACTIVE',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_employee_code unique(company_id, employee_code),
  constraint uq_employee_subject unique(company_id, keycloak_subject),
  constraint fk_employee_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_employee_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_employee_manager foreign key(manager_employee_id) references employees(id) on delete set null
);

create table employee_private_profiles (
  employee_id char(36) primary key, company_id char(36) not null,
  date_of_birth date null, identity_type varchar(40) null, identity_number_ciphertext text null,
  personal_email_ciphertext text null, personal_phone_ciphertext text null, address_ciphertext text null,
  emergency_name_ciphertext text null, emergency_phone_ciphertext text null,
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_employee_private_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_employee_private_company foreign key(company_id) references companies(id) on delete cascade
);

create table employee_documents (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  document_type varchar(60) not null, display_name varchar(255) not null, object_key varchar(500) not null,
  classification varchar(30) not null default 'PRIVATE', malware_scan_status varchar(30) not null default 'PENDING',
  uploaded_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_employee_document_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_employee_document_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table recruitment_positions (
  id char(36) primary key, company_id char(36) not null, department_id char(36) null,
  code varchar(50) not null, title varchar(180) not null, openings int not null default 1,
  status varchar(30) not null default 'DRAFT', hiring_manager_employee_id char(36) null,
  description text null, opened_on date null, closes_on date null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_recruitment_position unique(company_id,code),
  constraint fk_position_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_position_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_position_manager foreign key(hiring_manager_employee_id) references employees(id) on delete set null
);

create table recruitment_candidates (
  id char(36) primary key, company_id char(36) not null, position_id char(36) not null,
  full_name varchar(180) not null, email_ciphertext text not null, phone_ciphertext text null,
  cv_object_key varchar(500) null, source varchar(80) null, stage varchar(40) not null default 'APPLIED',
  status varchar(30) not null default 'ACTIVE', applied_at timestamp(6) not null default current_timestamp(6),
  constraint fk_candidate_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_candidate_position foreign key(position_id) references recruitment_positions(id) on delete cascade
);

create table recruitment_interviews (
  id char(36) primary key, company_id char(36) not null, candidate_id char(36) not null,
  round_name varchar(100) not null, scheduled_at timestamp(6) not null, duration_minutes int not null default 60,
  location_or_url varchar(500) null, interviewer_subjects text not null, status varchar(30) not null default 'SCHEDULED',
  score decimal(5,2) null, recommendation varchar(30) null, feedback text null,
  constraint fk_interview_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_interview_candidate foreign key(candidate_id) references recruitment_candidates(id) on delete cascade
);

create table recruitment_offers (
  id char(36) primary key, company_id char(36) not null, candidate_id char(36) not null,
  status varchar(30) not null default 'DRAFT', proposed_title varchar(180) not null,
  proposed_start_date date null, compensation_ciphertext text null, offer_object_key varchar(500) null,
  sent_at timestamp(6) null, responded_at timestamp(6) null,
  constraint fk_offer_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_offer_candidate foreign key(candidate_id) references recruitment_candidates(id) on delete cascade
);

create table onboarding_cases (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  start_date date not null, mentor_employee_id char(36) null, status varchar(30) not null default 'PLANNED',
  progress_percent decimal(5,2) not null default 0, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_onboarding_employee unique(employee_id),
  constraint fk_onboarding_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_onboarding_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_onboarding_mentor foreign key(mentor_employee_id) references employees(id) on delete set null
);

create table onboarding_tasks (
  id char(36) primary key, company_id char(36) not null, onboarding_case_id char(36) not null,
  category varchar(40) not null, title varchar(255) not null, assignee_subject varchar(100) null,
  due_on date null, status varchar(30) not null default 'PENDING', completed_at timestamp(6) null,
  constraint fk_onboarding_task_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_onboarding_task_case foreign key(onboarding_case_id) references onboarding_cases(id) on delete cascade
);

create table work_shifts (
  id char(36) primary key, company_id char(36) not null, code varchar(40) not null, name varchar(120) not null,
  start_time time not null, end_time time not null, break_minutes int not null default 0,
  late_grace_minutes int not null default 0, active boolean not null default true,
  constraint uq_work_shift unique(company_id,code),
  constraint fk_shift_company foreign key(company_id) references companies(id) on delete cascade
);

create table attendance_entries (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null, shift_id char(36) null,
  work_date date not null, check_in timestamp(6) null, check_out timestamp(6) null,
  worked_minutes int null, late_minutes int not null default 0, overtime_minutes int not null default 0,
  source varchar(30) not null default 'WEB', status varchar(30) not null default 'RECORDED',
  constraint uq_attendance_day unique(company_id,employee_id,work_date),
  constraint fk_attendance_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_attendance_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_attendance_shift foreign key(shift_id) references work_shifts(id) on delete set null
);

create table attendance_adjustment_requests (
  id char(36) primary key, company_id char(36) not null, attendance_entry_id char(36) not null,
  requested_by_subject varchar(100) not null, proposed_check_in timestamp(6) null, proposed_check_out timestamp(6) null,
  reason varchar(500) not null, status varchar(30) not null default 'PENDING',
  reviewed_by_subject varchar(100) null, reviewed_at timestamp(6) null, review_note varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_adjustment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_adjustment_attendance foreign key(attendance_entry_id) references attendance_entries(id) on delete cascade
);

create table leave_types (
  id char(36) primary key, company_id char(36) not null, code varchar(40) not null, name varchar(120) not null,
  paid boolean not null default true, annual_allowance decimal(7,2) null, requires_document boolean not null default false,
  active boolean not null default true, constraint uq_leave_type unique(company_id,code),
  constraint fk_leave_type_company foreign key(company_id) references companies(id) on delete cascade
);

create table leave_balances (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null, leave_type_id char(36) not null,
  balance_year int not null, entitled_days decimal(7,2) not null default 0, used_days decimal(7,2) not null default 0,
  pending_days decimal(7,2) not null default 0,
  constraint uq_leave_balance unique(employee_id,leave_type_id,balance_year),
  constraint fk_leave_balance_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_leave_balance_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_leave_balance_type foreign key(leave_type_id) references leave_types(id)
);

create table leave_requests (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null, leave_type_id char(36) not null,
  starts_on date not null, ends_on date not null, requested_days decimal(7,2) not null,
  reason varchar(500) null, document_object_key varchar(500) null, status varchar(30) not null default 'PENDING',
  approver_subject varchar(100) null, reviewed_at timestamp(6) null, review_note varchar(500) null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_leave_request_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_leave_request_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_leave_request_type foreign key(leave_type_id) references leave_types(id)
);

create table salary_structures (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  effective_from date not null, effective_to date null, currency char(3) not null,
  base_salary_ciphertext text not null, allowances_ciphertext text null, deductions_ciphertext text null,
  created_by_subject varchar(100) not null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_salary_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_salary_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table payroll_runs (
  id char(36) primary key, company_id char(36) not null, period_start date not null, period_end date not null,
  status varchar(30) not null default 'DRAFT', currency char(3) not null, approved_by_subject varchar(100) null,
  approved_at timestamp(6) null, paid_at timestamp(6) null, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_payroll_period unique(company_id,period_start,period_end),
  constraint fk_payroll_run_company foreign key(company_id) references companies(id) on delete cascade
);

create table payslips (
  id char(36) primary key, company_id char(36) not null, payroll_run_id char(36) not null, employee_id char(36) not null,
  gross_ciphertext text not null, deduction_ciphertext text not null, net_ciphertext text not null,
  payslip_object_key varchar(500) null, published_at timestamp(6) null,
  constraint uq_payslip_employee_run unique(payroll_run_id,employee_id),
  constraint fk_payslip_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_payslip_run foreign key(payroll_run_id) references payroll_runs(id) on delete cascade,
  constraint fk_payslip_employee foreign key(employee_id) references employees(id)
);

create table payslip_components (
  id char(36) primary key, company_id char(36) not null, payslip_id char(36) not null,
  component_type varchar(30) not null, code varchar(60) not null, name varchar(180) not null,
  amount_ciphertext text not null, display_order int not null default 0,
  constraint fk_payslip_component_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_payslip_component_slip foreign key(payslip_id) references payslips(id) on delete cascade
);

create table performance_cycles (
  id char(36) primary key, company_id char(36) not null, name varchar(180) not null,
  cycle_type varchar(30) not null, starts_on date not null, ends_on date not null, status varchar(30) not null default 'DRAFT',
  constraint fk_performance_cycle_company foreign key(company_id) references companies(id) on delete cascade
);

create table employee_objectives (
  id char(36) primary key, company_id char(36) not null, cycle_id char(36) not null, employee_id char(36) not null,
  objective_type varchar(20) not null, title varchar(255) not null, description text null,
  target_value decimal(15,4) null, current_value decimal(15,4) null, weight decimal(5,2) null,
  status varchar(30) not null default 'ACTIVE',
  constraint fk_objective_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_objective_cycle foreign key(cycle_id) references performance_cycles(id) on delete cascade,
  constraint fk_objective_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table performance_reviews (
  id char(36) primary key, company_id char(36) not null, cycle_id char(36) not null, employee_id char(36) not null,
  reviewer_employee_id char(36) not null, review_type varchar(30) not null, status varchar(30) not null default 'DRAFT',
  score decimal(5,2) null, strengths text null, improvements text null, development_plan text null,
  submitted_at timestamp(6) null,
  constraint fk_performance_review_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_performance_review_cycle foreign key(cycle_id) references performance_cycles(id) on delete cascade,
  constraint fk_performance_review_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_performance_review_reviewer foreign key(reviewer_employee_id) references employees(id)
);

create table employee_feedback (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  author_employee_id char(36) not null, visibility varchar(30) not null default 'EMPLOYEE_AND_HR',
  feedback text not null, created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_feedback_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_feedback_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_feedback_author foreign key(author_employee_id) references employees(id)
);

create table training_courses (
  id char(36) primary key, company_id char(36) not null, code varchar(50) not null, title varchar(255) not null,
  description text null, delivery_type varchar(30) not null, material_object_keys text null,
  passing_score decimal(5,2) null, active boolean not null default true,
  constraint uq_training_course unique(company_id,code),
  constraint fk_training_course_company foreign key(company_id) references companies(id) on delete cascade
);

create table training_enrollments (
  id char(36) primary key, company_id char(36) not null, course_id char(36) not null, employee_id char(36) not null,
  status varchar(30) not null default 'ASSIGNED', progress_percent decimal(5,2) not null default 0,
  score decimal(5,2) null, assigned_at timestamp(6) not null default current_timestamp(6), completed_at timestamp(6) null,
  constraint uq_training_enrollment unique(course_id,employee_id),
  constraint fk_training_enrollment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_training_enrollment_course foreign key(course_id) references training_courses(id) on delete cascade,
  constraint fk_training_enrollment_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table employee_certificates (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  name varchar(255) not null, issuer varchar(180) null, issued_on date null, expires_on date null,
  credential_id varchar(180) null, document_object_key varchar(500) null,
  constraint fk_certificate_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_certificate_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table employee_skills (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  skill_name varchar(180) not null, proficiency varchar(30) not null, verified_by_employee_id char(36) null,
  verified_at timestamp(6) null, constraint uq_employee_skill unique(employee_id,skill_name),
  constraint fk_skill_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_skill_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_skill_verifier foreign key(verified_by_employee_id) references employees(id) on delete set null
);

create table offboarding_cases (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  resignation_submitted_on date null, last_working_day date not null, reason_category varchar(60) null,
  reason_ciphertext text null, status varchar(30) not null default 'REQUESTED', archive_after date null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_offboarding_employee unique(employee_id),
  constraint fk_offboarding_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_offboarding_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table offboarding_tasks (
  id char(36) primary key, company_id char(36) not null, offboarding_case_id char(36) not null,
  category varchar(40) not null, title varchar(255) not null, assignee_subject varchar(100) null,
  asset_reference varchar(180) null, account_reference varchar(180) null,
  due_on date null, status varchar(30) not null default 'PENDING', completed_at timestamp(6) null,
  constraint fk_offboarding_task_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_offboarding_task_case foreign key(offboarding_case_id) references offboarding_cases(id) on delete cascade
);
