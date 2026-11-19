insert into permission_catalog(code,module,description,sensitive)
values ('pm.workload.write','PROJECT','Cấu hình năng lực làm việc của nhân viên',true)
on conflict(code) do update set module=excluded.module,description=excluded.description,sensitive=excluded.sensitive;

insert into company_role_permissions(role_id,permission_code)
select r.id,'pm.workload.write' from company_roles r where r.code='COMPANY_ADMIN'
on conflict do nothing;

insert into company_roles(id,company_id,code,name,description,system_role)
select substring(md5(c.id || ':PROJECT_MANAGER'),1,8) || '-' || substring(md5(c.id || ':PROJECT_MANAGER'),9,4) || '-' ||
       substring(md5(c.id || ':PROJECT_MANAGER'),13,4) || '-' || substring(md5(c.id || ':PROJECT_MANAGER'),17,4) || '-' ||
       substring(md5(c.id || ':PROJECT_MANAGER'),21,12),
       c.id,'PROJECT_MANAGER','Quản lý dự án','Quản lý dự án, nguồn lực, timesheet và sự cố trong phạm vi được giao',true
from companies c
on conflict(company_id,code) do update set name=excluded.name,description=excluded.description,active=true;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'company.read','hr.employee.read.basic','pm.access','pm.project.read','pm.project.write','pm.task.read','pm.task.write',
  'pm.sprint.read','pm.sprint.write','pm.timesheet.read','pm.timesheet.write','pm.timesheet.approve',
  'pm.workload.read','pm.workload.write','pm.report.read','pm.issue.read','pm.issue.write'
)
where r.code='PROJECT_MANAGER'
on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'pm.sprint.read','pm.timesheet.read.own','pm.timesheet.write','pm.issue.read','pm.issue.write'
)
where r.code='EMPLOYEE'
on conflict do nothing;

insert into company_role_permissions(role_id,permission_code)
select r.id,p.code from company_roles r join permission_catalog p on p.code in (
  'pm.sprint.read','pm.sprint.write','pm.timesheet.read','pm.timesheet.approve','pm.workload.read',
  'pm.workload.write','pm.report.read','pm.issue.read','pm.issue.write'
)
where r.code='MANAGER'
on conflict do nothing;

insert into company_membership_scopes(company_id,keycloak_subject,scope_type,scope_id)
select distinct m.company_id,e.keycloak_subject,'PROJECT',m.project_id
from pm_project_members m
join employees e on e.id=m.employee_id and e.company_id=m.company_id
join company_memberships cm on cm.company_id=m.company_id and cm.keycloak_subject=e.keycloak_subject
where e.keycloak_subject is not null and cm.status='ACTIVE'
on conflict do nothing;

create index if not exists ix_pm_project_company_department on pm_projects(company_id,department_id);
create index if not exists ix_pm_project_member_employee on pm_project_members(company_id,employee_id,project_id);
create index if not exists ix_pm_task_company_project on pm_tasks(company_id,project_id,status_id);
create index if not exists ix_pm_task_company_assignee on pm_tasks(company_id,assignee_employee_id,due_date);
create index if not exists ix_pm_sprint_company_project on pm_sprints(company_id,project_id,status);
create index if not exists ix_pm_timesheet_company_project on pm_timesheets(company_id,project_id,status);
create index if not exists ix_pm_issue_company_project on pm_issues(company_id,project_id,status);
