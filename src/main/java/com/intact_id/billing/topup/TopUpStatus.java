package com.intact_id.billing.topup;

/** Bank receipts: PENDING_REVIEW, then APPROVED, REJECTED or CANCELLED. Partner payments: PENDING_PAYMENT, then PAID or FAILED. */
public enum TopUpStatus {
    PENDING_REVIEW, APPROVED, REJECTED, CANCELLED, PENDING_PAYMENT, PAID, FAILED;

    public boolean isFinal() {
        return this != PENDING_REVIEW && this != PENDING_PAYMENT;
    }
}
