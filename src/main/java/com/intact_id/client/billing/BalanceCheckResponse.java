package com.intact_id.client.billing;

import java.math.BigDecimal;

/** Mirrors BalanceCheckResponseDto in billing-service. reason is null when allowed=true. */
public record BalanceCheckResponse(boolean allowed, BigDecimal requiredAmount, String currency, BalanceCheckReason reason) {
}
