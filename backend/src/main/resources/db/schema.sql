set names utf8mb4;

-- Legacy table names kept here only for clean development re-initialization.
drop table if exists seckill_orders;
drop table if exists seckill_activities;
drop table if exists venue_orders;
drop table if exists venue_product_inventory;
drop table if exists venue_products;
drop table if exists order_items;
drop table if exists orders;
drop table if exists cart_items;
drop table if exists products;
drop table if exists product_categories;
drop table if exists venues;

drop table if exists sport_activity_members;
drop table if exists sport_activities;
drop table if exists player_profiles;
drop table if exists file_metadata;
drop table if exists order_venue;
drop table if exists venue_inventory;
drop table if exists venue;
drop table if exists coaches;
drop table if exists order_seckill_venue;
drop table if exists seckill_venue;
drop table if exists order_seckill_equipment;
drop table if exists seckill_equipment;
drop table if exists order_equipment_item;
drop table if exists order_equipment;
drop table if exists cart_equipment;
drop table if exists equipment;
drop table if exists equipment_categories;
drop table if exists blogs;
drop table if exists follows;
drop table if exists venue_favorites;
drop table if exists venue_reviews;
drop table if exists venue_time_slots;
drop table if exists venue_courts;
drop table if exists place;
drop table if exists users;

create table users (
  id bigint primary key auto_increment,
  phone varchar(20) not null,
  email varchar(128) null,
  username varchar(64) null,
  password_hash varchar(128) not null,
  nickname varchar(64) not null,
  avatar varchar(512) null,
  city varchar(64) not null default '西安',
  level varchar(32) not null default '新手',
  prefer_time varchar(128) null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_users_phone (phone),
  unique key uk_users_email (email),
  unique key uk_users_username (username),
  key idx_users_city_level (city, level)
) engine=InnoDB default charset=utf8mb4;

create table follows (
  id bigint primary key auto_increment,
  user_id bigint not null,
  follow_user_id bigint not null,
  created_at datetime not null default current_timestamp,
  unique key uk_follow_pair (user_id, follow_user_id),
  key idx_follow_user (user_id),
  key idx_follow_target (follow_user_id)
) engine=InnoDB default charset=utf8mb4;

create table place (
  id bigint primary key auto_increment,
  sport_code varchar(32) not null default 'badminton',
  name varchar(128) not null,
  city varchar(64) not null,
  area varchar(64) not null,
  address varchar(255) not null,
  longitude decimal(10, 6) not null,
  latitude decimal(10, 6) not null,
  avg_price int not null default 0,
  score decimal(3, 1) not null default 5.0,
  review_count int not null default 0,
  open_hours varchar(64) not null,
  cover_url varchar(512) not null,
  facilities varchar(512) null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_venues_sport_city_area (sport_code, city, area),
  key idx_venues_score (score, review_count),
  key idx_venues_geo (longitude, latitude)
) engine=InnoDB default charset=utf8mb4;

create table venue_courts (
  id bigint primary key auto_increment,
  venue_id bigint not null,
  sport_code varchar(32) not null default 'badminton',
  name varchar(64) not null,
  court_type varchar(32) not null default '羽毛球',
  floor_type varchar(32) not null default '木地板',
  price_per_hour decimal(10, 2) not null,
  status varchar(16) not null default '可预订',
  created_at datetime not null default current_timestamp,
  key idx_courts_venue (venue_id)
) engine=InnoDB default charset=utf8mb4;

create table venue_time_slots (
  id bigint primary key auto_increment,
  venue_id bigint not null,
  court_id bigint not null,
  slot_date date not null,
  start_time time not null,
  end_time time not null,
  price decimal(10, 2) not null,
  status varchar(16) not null default '空闲',
  created_at datetime not null default current_timestamp,
  unique key uk_slot (court_id, slot_date, start_time),
  key idx_slots_venue_date (venue_id, slot_date)
) engine=InnoDB default charset=utf8mb4;

create table venue_reviews (
  id bigint primary key auto_increment,
  venue_id bigint not null,
  user_id bigint not null,
  rating int not null,
  content varchar(1000) not null,
  image_urls varchar(2000) null,
  likes int not null default 0,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_reviews_venue (venue_id, created_at),
  key idx_reviews_user (user_id)
) engine=InnoDB default charset=utf8mb4;

create table venue_favorites (
  id bigint primary key auto_increment,
  user_id bigint not null,
  venue_id bigint not null,
  created_at datetime not null default current_timestamp,
  unique key uk_favorite (user_id, venue_id),
  key idx_favorite_user (user_id)
) engine=InnoDB default charset=utf8mb4;

create table coaches (
  id bigint primary key auto_increment,
  venue_id bigint null,
  amap_place_id varchar(128) null,
  venue_name varchar(128) not null,
  sport_code varchar(32) not null,
  name varchar(64) not null,
  avatar varchar(512) null,
  level varchar(32) not null,
  tags varchar(255) null,
  intro varchar(512) null,
  price_per_hour decimal(10, 2) not null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_coaches_place (amap_place_id, sport_code),
  key idx_coaches_venue (venue_id, sport_code)
) engine=InnoDB default charset=utf8mb4;

create table venue (
  id bigint primary key auto_increment,
  venue_id bigint null,
  amap_place_id varchar(128) null,
  venue_name varchar(128) not null,
  place_rank int null,
  sport_code varchar(32) not null,
  product_type varchar(32) not null,
  title varchar(128) not null,
  description varchar(1000) null,
  cover_url varchar(512) null,
  price decimal(10, 2) not null,
  original_price decimal(10, 2) null,
  tags varchar(255) null,
  use_rule varchar(1000) null,
  refund_rule varchar(512) null,
  sale_start_at datetime null,
  sale_end_at datetime null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_venue_place (amap_place_id, sport_code, status),
  key idx_venue_venue (venue_id, sport_code, status),
  key idx_venue_rank (place_rank, sport_code, status),
  key idx_venue_type (sport_code, product_type, status)
) engine=InnoDB default charset=utf8mb4;

create table venue_inventory (
  id bigint primary key auto_increment,
  product_id bigint not null,
  venue_id bigint null,
  court_name varchar(64) null,
  coach_id bigint null,
  service_date date not null,
  start_time time not null,
  end_time time not null,
  total_stock int not null,
  available_stock int not null,
  locked_stock int not null default 0,
  sold_stock int not null default 0,
  price decimal(10, 2) not null,
  status varchar(16) not null default '可售',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_inventory_product_date (product_id, service_date, start_time),
  key idx_inventory_coach_date (coach_id, service_date, start_time),
  key idx_inventory_status (status, available_stock)
) engine=InnoDB default charset=utf8mb4;

create table order_venue (
  id bigint primary key auto_increment,
  user_id bigint not null,
  product_id bigint not null,
  inventory_id bigint not null,
  venue_id bigint null,
  amap_place_id varchar(128) null,
  venue_name varchar(128) not null,
  product_title varchar(128) not null,
  product_type varchar(32) not null,
  service_date date not null,
  start_time time not null,
  end_time time not null,
  amount decimal(10, 2) not null,
  status varchar(16) not null default '待支付',
  verify_code varchar(16) not null,
  paid_at datetime null,
  used_at datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_order_venue_user (user_id, created_at),
  key idx_order_venue_product (product_id, inventory_id),
  key idx_order_venue_verify (verify_code)
) engine=InnoDB default charset=utf8mb4;

create table equipment_categories (
  id bigint primary key auto_increment,
  sport_code varchar(32) not null default 'badminton',
  name varchar(64) not null,
  icon varchar(64) not null,
  sort int not null default 0,
  key idx_categories_sport (sport_code, sort)
) engine=InnoDB default charset=utf8mb4;

create table equipment (
  id bigint primary key auto_increment,
  sport_code varchar(32) not null default 'badminton',
  category_id bigint not null,
  name varchar(128) not null,
  brand varchar(64) not null,
  description varchar(1000) null,
  cover_url varchar(512) not null,
  price decimal(10, 2) not null,
  stock int not null default 0,
  score decimal(3, 1) not null default 5.0,
  sold int not null default 0,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_products_sport_category (sport_code, category_id),
  key idx_products_sales (sold, score),
  key idx_products_name (name)
) engine=InnoDB default charset=utf8mb4;

create table blogs (
  id bigint primary key auto_increment,
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
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_blogs_recommend (status, liked, created_at),
  key idx_blogs_sport (sport_code, status, created_at),
  key idx_blogs_user (user_id, created_at),
  key idx_blogs_related (related_type, related_id)
) engine=InnoDB default charset=utf8mb4;

create table cart_equipment (
  id bigint primary key auto_increment,
  user_id bigint not null,
  product_id bigint not null,
  quantity int not null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_cart_user_product (user_id, product_id),
  key idx_cart_user (user_id)
) engine=InnoDB default charset=utf8mb4;

create table order_equipment (
  id bigint primary key auto_increment,
  user_id bigint not null,
  total_amount decimal(10, 2) not null,
  status varchar(16) not null default '待支付',
  address varchar(255) not null,
  paid_at datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_orders_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4;

create table order_equipment_item (
  id bigint primary key auto_increment,
  order_id bigint not null,
  product_id bigint not null,
  product_name varchar(128) not null,
  cover_url varchar(512) not null,
  price decimal(10, 2) not null,
  quantity int not null,
  key idx_order_equipment_item_order (order_id)
) engine=InnoDB default charset=utf8mb4;

create table seckill_equipment (
  id bigint primary key auto_increment,
  equipment_id bigint not null,
  seckill_price decimal(10, 2) not null,
  stock int not null,
  start_at datetime not null,
  end_at datetime not null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_seckill_time (start_at, end_at, status)
) engine=InnoDB default charset=utf8mb4;

create table order_seckill_equipment (
  id bigint primary key,
  seckill_id bigint not null,
  equipment_id bigint not null,
  user_id bigint not null,
  amount decimal(10, 2) not null,
  status varchar(16) not null default '已抢到',
  created_at datetime not null default current_timestamp,
  unique key uk_seckill_equipment_user (user_id, seckill_id),
  key idx_order_seckill_equipment_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4;

create table seckill_venue (
  id bigint primary key auto_increment,
  venue_id bigint not null,
  seckill_price decimal(10, 2) not null,
  stock int not null,
  start_at datetime not null,
  end_at datetime not null,
  status tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_seckill_venue_time (start_at, end_at, status)
) engine=InnoDB default charset=utf8mb4;

create table order_seckill_venue (
  id bigint primary key,
  seckill_id bigint not null,
  venue_id bigint not null,
  user_id bigint not null,
  amount decimal(10, 2) not null,
  status varchar(16) not null default '已抢到',
  created_at datetime not null default current_timestamp,
  unique key uk_seckill_venue_user (user_id, seckill_id),
  key idx_order_seckill_venue_user (user_id, created_at)
) engine=InnoDB default charset=utf8mb4;

create table player_profiles (
  user_id bigint primary key,
  sport_code varchar(32) not null default 'badminton',
  city varchar(64) not null,
  area varchar(64) not null,
  longitude decimal(10, 6) not null,
  latitude decimal(10, 6) not null,
  level varchar(32) not null,
  play_style varchar(64) null,
  available_time varchar(128) null,
  intro varchar(255) null,
  allow_invite tinyint not null default 1,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_profiles_sport_city_area_level (sport_code, city, area, level),
  key idx_profiles_geo (longitude, latitude)
) engine=InnoDB default charset=utf8mb4;

create table file_metadata (
  id bigint primary key auto_increment,
  owner_user_id bigint null,
  biz_type varchar(64) not null,
  biz_id bigint null,
  bucket_name varchar(128) not null,
  object_name varchar(512) not null,
  original_filename varchar(255) not null,
  content_type varchar(128) not null,
  file_size bigint not null,
  etag varchar(128) null,
  public_url varchar(1000) null,
  status varchar(16) not null default '可用',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_file_object (bucket_name, object_name),
  key idx_file_owner (owner_user_id, created_at),
  key idx_file_biz (biz_type, biz_id),
  key idx_file_status (status)
) engine=InnoDB default charset=utf8mb4;

create table sport_activities (
  id bigint primary key auto_increment,
  sport_code varchar(32) not null default 'badminton',
  creator_id bigint not null,
  venue_id bigint null,
  place_source varchar(32) not null default 'amap',
  place_id varchar(128) null,
  venue_name varchar(128) not null,
  title varchar(128) not null,
  city varchar(64) not null,
  start_time datetime not null,
  end_time datetime not null,
  max_players int not null,
  current_players int not null default 1,
  level_required varchar(32) not null,
  fee_type varchar(32) not null default 'AA',
  status varchar(16) not null default '招募中',
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  key idx_activities_sport_city_time (sport_code, city, start_time),
  key idx_activities_place (place_source, place_id),
  key idx_activities_status (status)
) engine=InnoDB default charset=utf8mb4;

create table sport_activity_members (
  id bigint primary key auto_increment,
  activity_id bigint not null,
  user_id bigint not null,
  role varchar(16) not null,
  status varchar(16) not null,
  created_at datetime not null default current_timestamp,
  unique key uk_activity_member (activity_id, user_id),
  key idx_members_user (user_id)
) engine=InnoDB default charset=utf8mb4;
