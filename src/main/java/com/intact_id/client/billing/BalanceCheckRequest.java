package com.intact_id.client.billing;

/**
 * Mirrors BalanceCheckRequestDto in billing-service's POST /v1/billing-profile/check-balance.
 * externalRef must be the verification id — the same id BillingService.publishBillingEvent
 * later uses as the Kafka event's eventId, so the eventual real debit's externalRef matches
 * this lock's externalRef and releases it instead of waiting out its TTL.
 */
public record BalanceCheckRequest(String clientIdentifier, String eventType, Boolean lock, String externalRef, Integer quantity) {
}
