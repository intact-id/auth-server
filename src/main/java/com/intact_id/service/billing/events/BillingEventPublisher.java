package com.intact_id.service.billing.events;

import java.util.Map;

public interface BillingEventPublisher {

    void publish(String clientIdentifier, KafkaBillingEventType eventType, int quantity, Map<String, Object> metadata);
}
