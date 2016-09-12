# --- !Ups

create table organization_members (
  user_id           bigint not null,
  organization_id   bigint not null,
  unique (user_id, organization_id)
);

# --- !Downs

drop table organization_members;
