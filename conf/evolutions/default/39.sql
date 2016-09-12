# --- !Ups

alter table organizations add column avatar_url varchar(255);
alter table organizations add column tagline varchar(255);
alter table organizations add column global_roles int[] not null default '{}';

# --- !Downs

alter table organizations drop column avatar_url;
alter table organizations drop column tagline;
alter table organizations drop column global_roles;
