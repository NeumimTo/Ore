# --- !Ups

create table organizations (
  id          bigint        primary key,
  created_at  timestamp     ,
  name        varchar(20)   ,
  password    varchar(60)   not null,
  owner_id    bigint        not null
);

# --- !Downs

drop table organizations;
