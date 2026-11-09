package com.intact_id.billing.client;

import java.util.Map;

/**
 * Mirrors BalanceCheckRequestDto in billing-service's POST /v1/billing-profile/check-balance.
 * externalRef must be the verification id — the same id BillingService.publishBillingEvent
 * later uses as the Kafka event's eventId, so the eventual real debit's externalRef matches
 * this lock's externalRef and releases it instead of waiting out its TTL.
 */
// metadata.workflowId makes billing-service lock that workflow's price instead of the client's default
public record BalanceCheckRequest(String clientIdentifier, String eventType, Boolean lock, String externalRef, Integer quantity,
                                  Map<String, Object> metadata) {
}
