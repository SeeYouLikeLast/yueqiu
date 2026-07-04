package com.hm.badminton.mapper.community;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hm.badminton.entity.Blog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    @Insert("""
            insert ignore into blog_archive(
              id, user_id, sport_code, title, content, image_urls,
              related_type, related_id, related_title, related_cover_url, related_price,
              liked, status, deleted_at, archived_at, created_at, updated_at
            )
            select id, user_id, sport_code, title, content, image_urls,
                   related_type, related_id, related_title, related_cover_url, related_price,
                   liked, status, deleted_at, now(), created_at, updated_at
            from blogs
            where status = 0
              and deleted_at is not null
              and archived_at is null
              and deleted_at < date_sub(now(), interval #{retentionDays} day)
            order by deleted_at asc
            limit #{batchSize}
            """)
    int archiveDeletedBlogs(@Param("retentionDays") int retentionDays, @Param("batchSize") int batchSize);

    @Update("""
            update blogs
            set archived_at = now()
            where status = 0
              and deleted_at is not null
              and archived_at is null
              and deleted_at < date_sub(now(), interval #{retentionDays} day)
            order by deleted_at asc
            limit #{batchSize}
            """)
    int markDeletedBlogsArchived(@Param("retentionDays") int retentionDays, @Param("batchSize") int batchSize);

    @Delete("""
            delete from blogs
            where status = 0
              and deleted_at is not null
              and archived_at is not null
              and deleted_at < date_sub(now(), interval #{retentionDays} day)
            order by deleted_at asc
            limit #{batchSize}
            """)
    int deleteExpiredDeletedBlogs(@Param("retentionDays") int retentionDays, @Param("batchSize") int batchSize);
}


