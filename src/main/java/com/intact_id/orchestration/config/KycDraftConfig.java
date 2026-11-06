package com.intact_id.orchestration.config;

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

    // A submit that hasn't finished in this time crashed; the draft is made editable again
    private Duration submittingTimeout = Duration.ofMinutes(10);

    private long maxDocumentSizeBytes = 10L * 1024 * 1024;

    // How long a presigned direct-upload URL stays valid
    private Duration uploadUrlTtl = Duration.ofMinutes(5);
}
