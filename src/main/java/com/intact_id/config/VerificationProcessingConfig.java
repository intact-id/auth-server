package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "verification.processing")
public class VerificationProcessingConfig {

    private boolean enabled = true;

    private long pollIntervalMs = 30000;

    private int batchSize = 5;

    private long delayBetweenCallsMs = 30000;
}
