insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'TASK','BACKLOG','Backlog',0,'#6b7f8a',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'TASK','TODO','Đến làm',1,'#5b8def',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'TASK','IN_PROGRESS','Đang thực hiện',2,'#e0a52c',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'TASK','IN_REVIEW','Đang xem xét',3,'#9b6bd6',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'TASK','DONE','Hoàn thành',4,'#43a477',true,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'ISSUE','OPEN','Mở',0,'#b23b32',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'ISSUE','IN_PROGRESS','Đang xử lý',1,'#e0a52c',false,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'ISSUE','RESOLVED','Đã khắc phục',2,'#43a477',true,true from companies;

insert into pm_workflow_statuses(id,company_id,workflow_type,code,name,sort_order,color,is_done,active)
select gen_random_uuid()::text,id,'ISSUE','CLOSED','Đóng',3,'#6b7f8a',true,true from companies;
