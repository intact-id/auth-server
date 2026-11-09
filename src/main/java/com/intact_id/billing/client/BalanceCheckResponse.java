package com.intact_id.billing.client;

import java.math.BigDecimal;

/** Mirrors BalanceCheckResponseDto in billing-service. reason is null when allowed=true. */
public record BalanceCheckResponse(boolean allowed, BigDecimal requiredAmount, String currency, BalanceCheckReason reason) {
}
