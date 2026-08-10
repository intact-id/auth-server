package com.intact_id.client.billing;

/** Mirrors BalanceCheckRequestDto in billing-service's POST /v1/billing-profile/check-balance. */
public record BalanceCheckRequest(String clientIdentifier, String eventType, Integer quantity) {
}
