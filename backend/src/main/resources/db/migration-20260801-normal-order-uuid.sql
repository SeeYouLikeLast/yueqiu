-- Keep bigint primary keys for joins, and expose an opaque UUID as the business order number.
alter table order_venue add column order_no varchar(36) null after id;
update order_venue set order_no = uuid() where order_no is null or order_no = '';
alter table order_venue modify column order_no varchar(36) not null;
alter table order_venue add unique key uk_order_venue_no (order_no);

alter table order_equipment add column order_no varchar(36) null after id;
update order_equipment set order_no = uuid() where order_no is null or order_no = '';
alter table order_equipment modify column order_no varchar(36) not null;
alter table order_equipment add unique key uk_order_equipment_no (order_no);
