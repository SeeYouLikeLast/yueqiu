alter table blogs
  add column deleted_at datetime null after status,
  add column archived_at datetime null after deleted_at;

create index idx_blogs_cleanup on blogs(status, deleted_at, archived_at);

create table if not exists blog_archive (
  id bigint primary key,
  user_id bigint not null,
  sport_code varchar(32) not null,
  title varchar(128) not null,
  content varchar(2000) not null,
  image_urls varchar(3000) null,
  related_type varchar(32) not null,
  related_id bigint not null,
  related_title varchar(128) not null,
  related_cover_url varchar(512) null,
  related_price decimal(10, 2) null,
  liked int not null default 0,
  status tinyint not null default 0,
  deleted_at datetime not null,
  archived_at datetime not null default current_timestamp,
  created_at datetime not null,
  updated_at datetime not null,
  key idx_blog_archive_user (user_id, created_at),
  key idx_blog_archive_deleted_at (deleted_at)
) engine=InnoDB default charset=utf8mb4;
