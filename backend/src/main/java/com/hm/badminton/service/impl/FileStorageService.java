package com.hm.badminton.service.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.config.MinioProperties;
import com.hm.badminton.entity.FileMetadata;
import com.hm.badminton.mapper.FileMetadataMapper;
import com.hm.badminton.service.IFileStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService implements IFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final FileMetadataMapper fileMetadataMapper;

    public FileStorageService(MinioClient minioClient, MinioProperties properties, FileMetadataMapper fileMetadataMapper) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.fileMetadataMapper = fileMetadataMapper;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            ensureBucketRequired();
        } catch (Exception e) {
            log.warn("MinIO 暂不可用，文件上传会在调用时重试初始化 bucket：{}", e.getMessage());
        }
    }

    private void ensureBucketRequired() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(properties.getBucket())
                        .build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO bucket 初始化失败：" + e.getMessage(), e);
        }
    }

    @Transactional
    public FileMetadata upload(Long userId, MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        String objectName = buildObjectName(bizType, originalFilename);
        try {
            ensureBucketRequired();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
            String publicUrl = buildPublicUrl(objectName);
            FileMetadataMapper.InsertFileMetadataRow row = new FileMetadataMapper.InsertFileMetadataRow();
            row.setOwnerUserId(userId);
            row.setBizType(normalizeBizType(bizType));
            row.setBizId(bizId);
            row.setBucketName(properties.getBucket());
            row.setObjectName(objectName);
            row.setOriginalFilename(originalFilename);
            row.setContentType(contentType);
            row.setFileSize(file.getSize());
            row.setEtag(stat.etag());
            row.setPublicUrl(publicUrl);
            fileMetadataMapper.insertMetadata(row);
            return detail(row.getId());
        } catch (Exception e) {
            throw new BusinessException(500, "文件上传失败：" + e.getMessage());
        }
    }

    public FileMetadata detail(Long id) {
        FileMetadata metadata = fileMetadataMapper.selectById(id);
        if (metadata == null) {
            throw new BusinessException(404, "文件不存在");
        }
        return metadata;
    }

    public List<FileMetadata> list(String bizType, Long bizId, Long ownerUserId) {
        if (bizType != null && bizId != null) {
            return fileMetadataMapper.selectByBiz(normalizeBizType(bizType), bizId);
        }
        return fileMetadataMapper.selectByOwner(ownerUserId);
    }

    public ResponseEntity<InputStreamResource> download(Long id) {
        FileMetadata metadata = detail(id);
        try {
            var stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(metadata.bucketName())
                    .object(metadata.objectName())
                    .build());
            String encoded = URLEncoder.encode(metadata.originalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.contentType()))
                    .contentLength(metadata.fileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(encoded, StandardCharsets.UTF_8).build().toString())
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            throw new BusinessException(500, "文件下载失败：" + e.getMessage());
        }
    }

    public Map<String, String> presignedUrl(Long id) {
        FileMetadata metadata = detail(id);
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(metadata.bucketName())
                    .object(metadata.objectName())
                    .expiry(60 * 30)
                    .build());
            return Map.of("url", url);
        } catch (Exception e) {
            throw new BusinessException(500, "生成临时访问地址失败：" + e.getMessage());
        }
    }

    @Transactional
    public void remove(Long id) {
        FileMetadata metadata = detail(id);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(metadata.bucketName())
                    .object(metadata.objectName())
                    .build());
        } catch (Exception e) {
            throw new BusinessException(500, "删除 MinIO 文件失败：" + e.getMessage());
        }
        fileMetadataMapper.markDeleted(id);
    }

    private String buildObjectName(String bizType, String originalFilename) {
        String suffix = "";
        int index = originalFilename.lastIndexOf('.');
        if (index >= 0 && index < originalFilename.length() - 1) {
            suffix = originalFilename.substring(index).toLowerCase();
        }
        LocalDate today = LocalDate.now();
        return "%s/%d/%02d/%02d/%s%s".formatted(
                normalizeBizType(bizType),
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                suffix);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }
        return filename.replace("\\", "_").replace("/", "_").trim();
    }

    private String normalizeBizType(String bizType) {
        return bizType == null || bizType.isBlank() ? "common" : bizType.trim().toLowerCase();
    }

    private String buildPublicUrl(String objectName) {
        return properties.getPublicEndpoint().replaceAll("/+$", "") + "/" + properties.getBucket() + "/" + objectName;
    }

}

