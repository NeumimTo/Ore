# --- !Ups

alter table projects add column organization_id bigint;

# --- !Downs

alter table projects drop column organization_id;
