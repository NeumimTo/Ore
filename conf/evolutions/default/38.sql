# --- !Ups

alter table organizations alter column password set data type varchar(100);

# --- !Downs

alter table organizations alter column password set data type varchar(60);
