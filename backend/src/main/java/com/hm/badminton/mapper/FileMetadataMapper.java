package com.hm.badminton.mapper;

import lombok.Data;

import com.hm.badminton.entity.FileMetadata;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileMetadataMapper {

    @Insert("""
            insert into file_metadata(owner_user_id, biz_type, biz_id, bucket_name, object_name,
                                      original_filename, content_type, file_size, etag, public_url)
            values (#{row.ownerUserId}, #{row.bizType}, #{row.bizId}, #{row.bucketName}, #{row.objectName},
                    #{row.originalFilename}, #{row.contentType}, #{row.fileSize}, #{row.etag}, #{row.publicUrl})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertMetadata(@Param("row") InsertFileMetadataRow row);

    @Select("""
            select id, owner_user_id, biz_type, biz_id, bucket_name, object_name, original_filename,
                   content_type, file_size, etag, public_url, status, created_at
            from file_metadata
            where id = #{id}
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "owner_user_id", javaType = Long.class),
            @Arg(column = "biz_type", javaType = String.class),
            @Arg(column = "biz_id", javaType = Long.class),
            @Arg(column = "bucket_name", javaType = String.class),
            @Arg(column = "object_name", javaType = String.class),
            @Arg(column = "original_filename", javaType = String.class),
            @Arg(column = "content_type", javaType = String.class),
            @Arg(column = "file_size", javaType = Long.class),
            @Arg(column = "etag", javaType = String.class),
            @Arg(column = "public_url", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    FileMetadata selectById(@Param("id") Long id);

    @Select("""
            select id, owner_user_id, biz_type, biz_id, bucket_name, object_name, original_filename,
                   content_type, file_size, etag, public_url, status, created_at
            from file_metadata
            where biz_type = #{bizType} and biz_id = #{bizId} and status = '可用'
            order by created_at desc
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "owner_user_id", javaType = Long.class),
            @Arg(column = "biz_type", javaType = String.class),
            @Arg(column = "biz_id", javaType = Long.class),
            @Arg(column = "bucket_name", javaType = String.class),
            @Arg(column = "object_name", javaType = String.class),
            @Arg(column = "original_filename", javaType = String.class),
            @Arg(column = "content_type", javaType = String.class),
            @Arg(column = "file_size", javaType = Long.class),
            @Arg(column = "etag", javaType = String.class),
            @Arg(column = "public_url", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<FileMetadata> selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    @Select("""
            select id, owner_user_id, biz_type, biz_id, bucket_name, object_name, original_filename,
                   content_type, file_size, etag, public_url, status, created_at
            from file_metadata
            where owner_user_id = #{ownerUserId} and status = '可用'
            order by created_at desc
            limit 100
            """)
    @ConstructorArgs({
            @Arg(column = "id", javaType = Long.class),
            @Arg(column = "owner_user_id", javaType = Long.class),
            @Arg(column = "biz_type", javaType = String.class),
            @Arg(column = "biz_id", javaType = Long.class),
            @Arg(column = "bucket_name", javaType = String.class),
            @Arg(column = "object_name", javaType = String.class),
            @Arg(column = "original_filename", javaType = String.class),
            @Arg(column = "content_type", javaType = String.class),
            @Arg(column = "file_size", javaType = Long.class),
            @Arg(column = "etag", javaType = String.class),
            @Arg(column = "public_url", javaType = String.class),
            @Arg(column = "status", javaType = String.class),
            @Arg(column = "created_at", javaType = LocalDateTime.class)
    })
    List<FileMetadata> selectByOwner(@Param("ownerUserId") Long ownerUserId);

    @Update("update file_metadata set status = '已删除', updated_at = now() where id = #{id}")
    int markDeleted(@Param("id") Long id);

    @Data

    class InsertFileMetadataRow {
        private Long id;
        private Long ownerUserId;
        private String bizType;
        private Long bizId;
        private String bucketName;
        private String objectName;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String etag;
        private String publicUrl;
    }
}
