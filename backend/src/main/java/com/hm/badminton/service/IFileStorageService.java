package com.hm.badminton.service;

import com.hm.badminton.entity.FileMetadata;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface IFileStorageService {
    void ensureBucket();

    FileMetadata upload(Long userId, MultipartFile file, String bizType, Long bizId);

    FileMetadata detail(Long id);

    List<FileMetadata> list(String bizType, Long bizId, Long ownerUserId);

    ResponseEntity<InputStreamResource> download(Long id);

    Map<String, String> presignedUrl(Long id);

    void remove(Long id);
}
