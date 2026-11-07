insert into leave_types(id,company_id,code,name,paid,annual_allowance,requires_document,active)
select gen_random_uuid()::text,id,'ANNUAL','Phép năm',true,12,false,true from companies;

insert into leave_types(id,company_id,code,name,paid,annual_allowance,requires_document,active)
select gen_random_uuid()::text,id,'SICK','Nghỉ ốm',true,30,true,true from companies;

insert into leave_types(id,company_id,code,name,paid,annual_allowance,requires_document,active)
select gen_random_uuid()::text,id,'UNPAID','Nghỉ không lương',false,null,false,true from companies;

insert into work_shifts(id,company_id,code,name,start_time,end_time,break_minutes,late_grace_minutes,active)
select gen_random_uuid()::text,id,'OFFICE','Ca hành chính','08:30:00','17:30:00',60,10,true from companies;
