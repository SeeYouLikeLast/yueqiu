package com.hm.badminton.entity;

import java.time.LocalDateTime;

public record FileMetadata(
        Long id,
        Long ownerUserId,
        String bizType,
        Long bizId,
        String bucketName,
        String objectName,
        String originalFilename,
        String contentType,
        Long fileSize,
        String etag,
        String publicUrl,
        String status,
        LocalDateTime createdAt) {
}

