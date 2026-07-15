-- Public images are delivered by Nginx -> MinIO instead of Java -> MinIO.
-- This migration is idempotent and preserves external image URLs such as AMap photos.
delimiter $$

drop procedure if exists migrate_public_image_urls$$
create procedure migrate_public_image_urls()
begin
    declare finished int default 0;
    declare file_id bigint;
    declare bucket_name_value varchar(128);
    declare object_name_value varchar(512);
    declare old_url varchar(1024);
    declare new_url varchar(1024);

    declare file_cursor cursor for
        select id, bucket_name, object_name
        from file_metadata
        where status = '可用'
          and bucket_name is not null
          and object_name is not null;
    declare continue handler for not found set finished = 1;

    open file_cursor;
    migrate_loop: loop
        fetch file_cursor into file_id, bucket_name_value, object_name_value;
        if finished = 1 then
            leave migrate_loop;
        end if;

        set old_url = concat('/api/files/', file_id, '/download');
        set new_url = concat('/objects/', bucket_name_value, '/', object_name_value);

        update users set avatar = replace(avatar, old_url, new_url) where avatar like concat('%', old_url, '%');
        update place set cover_url = replace(cover_url, old_url, new_url) where cover_url like concat('%', old_url, '%');
        update venue_reviews set image_urls = replace(image_urls, old_url, new_url) where image_urls like concat('%', old_url, '%');
        update coaches set avatar = replace(avatar, old_url, new_url) where avatar like concat('%', old_url, '%');
        update venue_operators set avatar = replace(avatar, old_url, new_url) where avatar like concat('%', old_url, '%');
        update venue set cover_url = replace(cover_url, old_url, new_url) where cover_url like concat('%', old_url, '%');
        update equipment set cover_url = replace(cover_url, old_url, new_url) where cover_url like concat('%', old_url, '%');
        update blogs set image_urls = replace(image_urls, old_url, new_url) where image_urls like concat('%', old_url, '%');
        update blogs set related_cover_url = replace(related_cover_url, old_url, new_url) where related_cover_url like concat('%', old_url, '%');
        update blog_archive set image_urls = replace(image_urls, old_url, new_url) where image_urls like concat('%', old_url, '%');
        update blog_archive set related_cover_url = replace(related_cover_url, old_url, new_url) where related_cover_url like concat('%', old_url, '%');
        update order_equipment_item set cover_url = replace(cover_url, old_url, new_url) where cover_url like concat('%', old_url, '%');
        update file_metadata set public_url = new_url where id = file_id;
    end loop;
    close file_cursor;
end$$

call migrate_public_image_urls()$$
drop procedure migrate_public_image_urls$$

delimiter ;
