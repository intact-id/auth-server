package com.intact_id.service.billing.events;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Payload published to KafkaTopics.BILLING_EVENTS. Field names/shape are a contract with the
 * external billing consumer — do not rename without checking with that side.
 */
@Getter
@Builder
public class BillingEventMessage {
    private final String eventId;
    private final String clientIdentifier;
    private final KafkaBillingEventType eventType;
    private final int quantity;
    private final Instant occurredAt;
    private final Map<String, Object> metadata;
}
