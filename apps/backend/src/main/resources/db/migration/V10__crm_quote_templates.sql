create table crm_quote_templates (
  id char(36) primary key, company_id char(36) not null, name varchar(180) not null,
  title varchar(255) not null, number_prefix varchar(20) not null default 'QT', default_terms text null,
  footer text null, active boolean not null default true, created_at timestamp(6) not null default current_timestamp(6),
  constraint uq_crm_quote_template unique(company_id,name),
  constraint fk_crm_quote_template_company foreign key(company_id) references companies(id) on delete cascade
);

alter table crm_quotes add column quote_template_id char(36) null;
alter table crm_quotes add constraint fk_crm_quote_template
  foreign key(quote_template_id) references crm_quote_templates(id) on delete set null;

create index ix_crm_quote_template_active on crm_quote_templates(company_id,active,name);
