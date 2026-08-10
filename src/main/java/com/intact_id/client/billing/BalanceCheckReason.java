package com.intact_id.client.billing;

/**
 * Mirrors com.codify.apibilingservice.constant.enums.BalanceCheckReason in the billing-service
 * repo — keep in sync if that enum changes.
 */
public enum BalanceCheckReason {
    PROFILE_NOT_FOUND,
    PROFILE_INACTIVE,
    PRICING_NOT_CONFIGURED,
    WALLET_NOT_CONFIGURED,
    INSUFFICIENT_BALANCE
}
