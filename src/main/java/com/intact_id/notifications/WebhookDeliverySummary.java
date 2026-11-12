package com.intact_id.notifications;

import java.time.LocalDateTime;

/**
 * Delivery counts for one company since {@code since}, grouped by current delivery status.
 * A delivery that succeeded after a retry counts as delivered.
 */
public record WebhookDeliverySummary(
        LocalDateTime since,
        long total,
        long delivered,
        long failed,
        long retrying,
        long pending
) {
}
