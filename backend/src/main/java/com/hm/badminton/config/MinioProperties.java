package com.hm.badminton.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "hm.minio")
public class MinioProperties {

    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String bucket = "hm-badminton";
}

