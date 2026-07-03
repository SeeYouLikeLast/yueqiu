package com.hm.badminton.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class FileMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

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
    private String status;
    private LocalDateTime createdAt;
}

