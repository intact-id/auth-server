package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Settings for kyc.storage.type=s3 (Cloudflare R2, AWS S3 or MinIO). */
@Data
@Configuration
@ConfigurationProperties(prefix = "kyc.storage.s3")
public class S3StorageProperties {

    private String endpoint;

    private String bucket;

    private String region = "auto";

    private String accessKey;

    private String secretKey;
}
