package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Webhook configuration properties
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "webhook")
public class WebhookConfig {

    /**
     * Enable/disable webhook functionality
     */
    private boolean enabled = true;

    /**
     * Maximum number of retry attempts
     */
    private int maxRetries = 3;

    /**
     * HTTP request timeout in seconds
     */
    private int timeoutSeconds = 30;

    /**
     * Retry intervals in seconds (e.g., [60, 300, 900] = 1min, 5min, 15min)
     */
    private List<Integer> retryIntervals = List.of(60, 300, 900);

    /**
     * Signature algorithm (HmacSHA256)
     */
    private String signatureAlgorithm = "HmacSHA256";

    /**
     * Get retry interval for a specific attempt
     */
    public int getRetryInterval(int attemptNumber) {
        if (attemptNumber >= retryIntervals.size()) {
            return retryIntervals.get(retryIntervals.size() - 1);
        }
        return retryIntervals.get(attemptNumber);
    }
}
