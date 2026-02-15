package com.intact_id.dto.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook event data transfer object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEventDto {

    /**
     * Event type (e.g., verification.completed, verification.failed)
     */
    private String eventType;

    /**
     * Unique event ID
     */
    private String eventId;

    /**
     * Timestamp when event occurred
     */
    private LocalDateTime timestamp;

    /**
     * Company ID that owns this event
     */
    private String companyId;

    /**
     * Company legal name
     */
    private String companyName;

    /**
     * Verification ID related to this event
     */
    private String verificationId;

    /**
     * Event-specific data
     */
    private Map<String, Object> data;

    /**
     * API version
     */
    @Builder.Default
    private String apiVersion = "v1";
}
