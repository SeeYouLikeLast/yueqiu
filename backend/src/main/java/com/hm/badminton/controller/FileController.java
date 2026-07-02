package com.hm.badminton.controller;

import com.hm.badminton.utils.UserContext;
import com.hm.badminton.common.ApiResponse;
import com.hm.badminton.entity.FileMetadata;
import com.hm.badminton.service.IFileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileController {

    private final IFileStorageService fileStorageService;
    private final UserContext userContext;

    public FileController(IFileStorageService fileStorageService, UserContext userContext) {
        this.fileStorageService = fileStorageService;
        this.userContext = userContext;
    }

    @PostMapping("/upload")
    public ApiResponse<FileMetadata> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(defaultValue = "common") String bizType,
                                            @RequestParam(required = false) Long bizId) {
        Long userId = userContext.current().map(user -> user.getId()).orElse(null);
        return ApiResponse.ok(fileStorageService.upload(userId, file, bizType, bizId));
    }

    @GetMapping("/{id}")
    public ApiResponse<FileMetadata> detail(@PathVariable Long id) {
        return ApiResponse.ok(fileStorageService.detail(id));
    }

    @GetMapping
    public ApiResponse<List<FileMetadata>> list(@RequestParam(required = false) String bizType,
                                                @RequestParam(required = false) Long bizId) {
        Long userId = userContext.current().map(user -> user.getId()).orElse(null);
        return ApiResponse.ok(fileStorageService.list(bizType, bizId, userId));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        return fileStorageService.download(id);
    }

    @GetMapping("/{id}/presigned-url")
    public ApiResponse<Map<String, String>> presignedUrl(@PathVariable Long id) {
        return ApiResponse.ok(fileStorageService.presignedUrl(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        userContext.require();
        fileStorageService.remove(id);
        return ApiResponse.ok();
    }
}

