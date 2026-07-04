set @big_v_column_exists = (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'users'
    and column_name = 'is_big_v'
);

set @big_v_column_sql = if(
  @big_v_column_exists = 0,
  'alter table users add column is_big_v tinyint not null default 0 after prefer_time',
  'select 1'
);

prepare big_v_column_stmt from @big_v_column_sql;
execute big_v_column_stmt;
deallocate prepare big_v_column_stmt;

update users set is_big_v = 1 where id = 2;

insert ignore into follows(user_id, follow_user_id) values
(3, 2),
(4, 2),
(5, 2),
(6, 2),
(8, 2),
(9, 2),
(10, 2);
