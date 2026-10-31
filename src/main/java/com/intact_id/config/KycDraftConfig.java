package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kyc.drafts")
public class KycDraftConfig {

    private Duration ttl = Duration.ofHours(24);

    private Duration tokenTtl = Duration.ofMinutes(15);

    private long cleanupIntervalMs = 900_000;

    private int cleanupBatchSize = 200;

    private long maxDocumentSizeBytes = 10L * 1024 * 1024;
}
