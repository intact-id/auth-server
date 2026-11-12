package com.intact_id.notifications;

import java.time.LocalDateTime;

/**
 * One webhook delivery as shown in the console's delivery log.
 *
 * <p>Deliberately leaves out the payload and the receiver's response body: both can carry
 * applicant data, and a list view does not need them.
 */
public record WebhookDeliveryView(
        String id,
        String eventType,
        String verificationId,
        String webhookUrl,
        String status,
        Integer httpStatusCode,
        Integer retryCount,
        LocalDateTime nextRetryAt,
        String errorMessage,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {
}
