package com.intact_id.service.billing.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.config.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Publishes KYC billing/usage events to Kafka for the external billing consumer. Additive —
 * BillingService still owns local charges/wallet/credit-limit enforcement; this is a second,
 * independent output describing the same outcome, not a replacement for it (yet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaBillingEventPublisher implements BillingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String eventId, String clientIdentifier, KafkaBillingEventType eventType, int quantity, Map<String, Object> metadata) {
        BillingEventMessage message = BillingEventMessage.builder()
                .eventId(eventId)
                .clientIdentifier(clientIdentifier)
                .eventType(eventType)
                .quantity(quantity)
                .occurredAt(Instant.now())
                .metadata(metadata)
                .build();

        log.info("Sending billing event to topic {} (client={}): {}", KafkaTopics.BILLING_EVENTS, clientIdentifier, toJson(message));

        try {
            kafkaTemplate.send(KafkaTopics.BILLING_EVENTS, clientIdentifier, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Billing event {} failed to send for client {}: {}",
                                    message.getEventId(), clientIdentifier, ex.getMessage(), ex);
                        } else {
                            log.info("Billing event {} sent for client {} — partition={} offset={}",
                                    message.getEventId(), clientIdentifier,
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to publish billing event for client {}: {}", clientIdentifier, e.getMessage(), e);
        }
    }

    private String toJson(BillingEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return "<failed to serialize for logging: " + e.getMessage() + ">";
        }
    }
}
