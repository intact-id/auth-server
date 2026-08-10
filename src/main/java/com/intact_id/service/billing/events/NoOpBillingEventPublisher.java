package com.intact_id.service.billing.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Active while kafka.enabled=false (the default — no broker deployed yet). Keeps
 * BillingService's dependency on BillingEventPublisher unconditional, so flipping
 * kafka.enabled on later needs no code change here.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpBillingEventPublisher implements BillingEventPublisher {

    @Override
    public void publish(String clientIdentifier, KafkaBillingEventType eventType, int quantity, Map<String, Object> metadata) {
        log.debug("Kafka disabled — skipping billing event publish: client={}, eventType={}, quantity={}",
                clientIdentifier, eventType, quantity);
    }
}
