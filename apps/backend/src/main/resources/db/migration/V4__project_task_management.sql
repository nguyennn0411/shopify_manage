create table pm_projects (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, name varchar(255) not null,
  department_id char(36) null, owner_employee_id char(36) null, objective text null, scope text null,
  budget decimal(19,4) null, currency char(3) null, status varchar(30) not null default 'PLANNED',
  starts_on date null, ends_on date null, progress_percent decimal(5,2) not null default 0,
  risk_level varchar(20) not null default 'LOW',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_project_code unique(company_id, code),
  constraint fk_project_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_project_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_project_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table pm_project_members (
  project_id char(36) not null, company_id char(36) not null, employee_id char(36) not null,
  role varchar(30) not null default 'MEMBER', cost_rate decimal(19,4) null,
  created_at timestamp(6) not null default current_timestamp(6),
  primary key(project_id, employee_id),
  constraint fk_project_member_project foreign key(project_id) references pm_projects(id) on delete cascade,
  constraint fk_project_member_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_project_member_employee foreign key(employee_id) references employees(id) on delete cascade
);

create table pm_milestones (
  id char(36) primary key, company_id char(36) not null, project_id char(36) not null,
  title varchar(255) not null, due_date date not null, status varchar(30) not null default 'OPEN',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_milestone_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_milestone_project foreign key(project_id) references pm_projects(id) on delete cascade
);

create table pm_risks (
  id char(36) primary key, company_id char(36) not null, project_id char(36) not null,
  title varchar(255) not null, likelihood varchar(20) not null default 'MEDIUM', impact varchar(20) not null default 'MEDIUM',
  mitigation text null, status varchar(30) not null default 'OPEN', owner_employee_id char(36) null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_risk_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_risk_project foreign key(project_id) references pm_projects(id) on delete cascade,
  constraint fk_risk_owner foreign key(owner_employee_id) references employees(id) on delete set null
);

create table pm_sprints (
  id char(36) primary key, company_id char(36) not null, project_id char(36) not null,
  name varchar(180) not null, goal text null, starts_on date not null, ends_on date not null,
  status varchar(30) not null default 'PLANNED',
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_sprint_project unique(project_id, name),
  constraint fk_sprint_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_sprint_project foreign key(project_id) references pm_projects(id) on delete cascade
);

create table pm_workflow_statuses (
  id char(36) primary key, company_id char(36) not null, workflow_type varchar(20) not null default 'TASK',
  code varchar(40) not null, name varchar(120) not null, sort_order int not null default 0,
  color varchar(20) null, is_done boolean not null default false, active boolean not null default true,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_wf_status unique(company_id, workflow_type, code),
  constraint fk_wf_status_company foreign key(company_id) references companies(id) on delete cascade
);

create table pm_tasks (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null, title varchar(255) not null,
  description text null, project_id char(36) null, sprint_id char(36) null, department_id char(36) null,
  assignee_employee_id char(36) null, supervisor_employee_id char(36) null, parent_task_id char(36) null,
  status_id char(36) not null, priority varchar(20) not null default 'MEDIUM',
  progress_percent decimal(5,2) not null default 0, estimated_hours decimal(7,2) null,
  starts_on date null, due_date date null, watcher_subjects text null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_task_code unique(company_id, code),
  constraint fk_task_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_task_project foreign key(project_id) references pm_projects(id) on delete set null,
  constraint fk_task_sprint foreign key(sprint_id) references pm_sprints(id) on delete set null,
  constraint fk_task_department foreign key(department_id) references departments(id) on delete set null,
  constraint fk_task_assignee foreign key(assignee_employee_id) references employees(id) on delete set null,
  constraint fk_task_supervisor foreign key(supervisor_employee_id) references employees(id) on delete set null,
  constraint fk_task_parent foreign key(parent_task_id) references pm_tasks(id) on delete set null,
  constraint fk_task_status foreign key(status_id) references pm_workflow_statuses(id)
);

create table pm_task_dependencies (
  task_id char(36) not null, company_id char(36) not null, depends_on_task_id char(36) not null,
  dependency_type varchar(30) not null default 'FINISH_TO_START',
  created_at timestamp(6) not null default current_timestamp(6),
  primary key(task_id, depends_on_task_id),
  constraint fk_task_dep_task foreign key(task_id) references pm_tasks(id) on delete cascade,
  constraint fk_task_dep_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_task_dep_on foreign key(depends_on_task_id) references pm_tasks(id) on delete cascade
);

create table pm_task_checklist_items (
  id char(36) primary key, company_id char(36) not null, task_id char(36) not null,
  title varchar(255) not null, position int not null default 0, done boolean not null default false,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_task_checklist_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_task_checklist_task foreign key(task_id) references pm_tasks(id) on delete cascade
);

create table pm_task_comments (
  id char(36) primary key, company_id char(36) not null, task_id char(36) not null,
  author_subject varchar(100) not null, body text not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_task_comment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_task_comment_task foreign key(task_id) references pm_tasks(id) on delete cascade
);

create table pm_task_attachments (
  id char(36) primary key, company_id char(36) not null, task_id char(36) not null,
  display_name varchar(255) not null, object_key varchar(500) not null,
  classification varchar(30) not null default 'INTERNAL',
  uploaded_by_subject varchar(100) not null,
  created_at timestamp(6) not null default current_timestamp(6),
  constraint fk_task_attachment_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_task_attachment_task foreign key(task_id) references pm_tasks(id) on delete cascade
);

create table pm_timesheets (
  id char(36) primary key, company_id char(36) not null, employee_id char(36) not null,
  task_id char(36) null, project_id char(36) null, work_date date not null,
  hours decimal(7,2) not null, status varchar(30) not null default 'DRAFT',
  note varchar(500) null, reviewer_subject varchar(100) null, reviewed_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_timesheet_employee_date unique(company_id, employee_id, task_id, project_id, work_date),
  constraint fk_timesheet_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_timesheet_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_timesheet_task foreign key(task_id) references pm_tasks(id) on delete set null,
  constraint fk_timesheet_project foreign key(project_id) references pm_projects(id) on delete set null
);

create table pm_employee_capacity (
  employee_id char(36) primary key, company_id char(36) not null,
  weekly_capacity_hours decimal(7,2) not null default 40,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint fk_capacity_employee foreign key(employee_id) references employees(id) on delete cascade,
  constraint fk_capacity_company foreign key(company_id) references companies(id) on delete cascade
);

create table pm_issues (
  id char(36) primary key, company_id char(36) not null, code varchar(60) not null,
  type varchar(30) not null default 'ISSUE', severity varchar(20) not null default 'MEDIUM',
  status varchar(30) not null default 'OPEN', title varchar(255) not null, description text null,
  project_id char(36) null, task_id char(36) null, reporter_subject varchar(100) not null,
  assignee_employee_id char(36) null, resolution_note text null, resolved_at timestamp(6) null,
  created_at timestamp(6) not null default current_timestamp(6),
  updated_at timestamp(6) not null default current_timestamp(6),
  constraint uq_issue_code unique(company_id, code),
  constraint fk_issue_company foreign key(company_id) references companies(id) on delete cascade,
  constraint fk_issue_project foreign key(project_id) references pm_projects(id) on delete set null,
  constraint fk_issue_task foreign key(task_id) references pm_tasks(id) on delete set null,
  constraint fk_issue_assignee foreign key(assignee_employee_id) references employees(id) on delete set null
);
