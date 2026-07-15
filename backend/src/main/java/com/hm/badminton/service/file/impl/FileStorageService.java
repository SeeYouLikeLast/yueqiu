package com.hm.badminton.service.file.impl;

import com.hm.badminton.common.BusinessException;
import com.hm.badminton.config.MinioProperties;
import com.hm.badminton.entity.FileMetadata;
import com.hm.badminton.mapper.file.FileMetadataMapper;
import com.hm.badminton.service.file.IFileStorageService;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService implements IFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long AVATAR_MAX_SIZE = 5 * 1024 * 1024L;
    private static final long BLOG_IMAGE_MAX_SIZE = 10 * 1024 * 1024L;
    private static final long ATTACHMENT_MAX_SIZE = 20 * 1024 * 1024L;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");
    private static final Set<String> ATTACHMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final Set<String> ATTACHMENT_EXTENSIONS = Set.of(".pdf", ".docx", ".xlsx");

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
            throw new IllegalStateException("MinIO bucket 初始化失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public FileMetadata upload(Long userId, MultipartFile file, String bizType, Long bizId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String contentType = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType();
        String normalizedBizType = normalizeBizType(bizType);
        validateUploadFile(normalizedBizType, originalFilename, contentType, file.getSize());
        String objectName = buildObjectName(bizType, originalFilename);
        boolean objectUploaded = false;
        try {
            ensureBucketRequired();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build());
            objectUploaded = true;
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
            String publicUrl = buildPublicUrl(objectName);
            FileMetadataMapper.InsertFileMetadataRow row = new FileMetadataMapper.InsertFileMetadataRow();
            row.setOwnerUserId(userId);
            row.setBizType(normalizedBizType);
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
            if (objectUploaded) {
                removeObjectQuietly(properties.getBucket(), objectName);
            }
            throw new BusinessException(500, "文件上传失败: " + e.getMessage());
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
                    .bucket(metadata.getBucketName())
                    .object(metadata.getObjectName())
                    .build());
            String encoded = URLEncoder.encode(metadata.getOriginalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(metadata.getContentType()))
                    .contentLength(metadata.getFileSize())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(encoded, StandardCharsets.UTF_8).build().toString())
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            throw new BusinessException(500, "文件下载失败: " + e.getMessage());
        }
    }

    public Map<String, String> presignedUrl(Long id) {
        FileMetadata metadata = detail(id);
        try {
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(metadata.getBucketName())
                    .object(metadata.getObjectName())
                    .expiry(60 * 30)
                    .build());
            return Map.of("url", url);
        } catch (Exception e) {
            throw new BusinessException(500, "生成临时访问地址失败: " + e.getMessage());
        }
    }

    @Transactional
    public void remove(Long ownerUserId, Long id) {
        FileMetadata metadata = detail(id);
        requireOwner(ownerUserId, metadata);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(metadata.getBucketName())
                    .object(metadata.getObjectName())
                    .build());
        } catch (Exception e) {
            throw new BusinessException(500, "删除 MinIO 文件失败: " + e.getMessage());
        }
        fileMetadataMapper.markDeleted(id);
    }

    private void validateUploadFile(String bizType, String originalFilename, String contentType, long fileSize) {
        String suffix = fileSuffix(originalFilename);
        if (isImageBizType(bizType)) {
            long maxSize = "avatar".equals(bizType) ? AVATAR_MAX_SIZE : BLOG_IMAGE_MAX_SIZE;
            if (fileSize > maxSize) {
                throw new BusinessException("图片大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
            }
            if (!IMAGE_CONTENT_TYPES.contains(contentType) || !IMAGE_EXTENSIONS.contains(suffix)) {
                throw new BusinessException("只允许上传 jpg、png、webp 图片");
            }
            return;
        }
        if (fileSize > ATTACHMENT_MAX_SIZE) {
            throw new BusinessException("文件大小不能超过 20MB");
        }
        boolean image = IMAGE_CONTENT_TYPES.contains(contentType) && IMAGE_EXTENSIONS.contains(suffix);
        boolean attachment = ATTACHMENT_CONTENT_TYPES.contains(contentType) && ATTACHMENT_EXTENSIONS.contains(suffix);
        if (!image && !attachment) {
            throw new BusinessException("只允许上传 jpg、png、webp、pdf、docx、xlsx 文件");
        }
    }

    private boolean isImageBizType(String bizType) {
        return "avatar".equals(bizType) || "blog".equals(bizType) || "blog_image".equals(bizType) || "image".equals(bizType);
    }

    private void requireOwner(Long ownerUserId, FileMetadata metadata) {
        if (ownerUserId == null || metadata.getOwnerUserId() == null || !Objects.equals(ownerUserId, metadata.getOwnerUserId())) {
            throw new BusinessException(403, "无权操作该文件");
        }
    }

    private void removeObjectQuietly(String bucketName, String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception cleanupError) {
            log.warn("MinIO 孤儿文件补偿删除失败，bucket={}, object={}: {}", bucketName, objectName, cleanupError.getMessage());
        }
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

    private String fileSuffix(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index).toLowerCase();
    }

    private String buildPublicUrl(String objectName) {
        // Public media is served by Nginx directly from MinIO; Java only handles upload metadata and authorization.
        return "/objects/" + properties.getBucket() + "/" + objectName;
    }

}



