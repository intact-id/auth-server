package com.intact_id.billing.client;

/** Mirrors billing-service's generic ApiResult<T> response envelope. */
public record BillingApiResult<T>(T data, String status, String message, Boolean isTechnical) {
}
