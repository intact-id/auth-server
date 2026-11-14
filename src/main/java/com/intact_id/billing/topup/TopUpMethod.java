package com.intact_id.billing.topup;

/** How a company tops up its wallet. Card, M-Pesa and Airtel Money go through the payment partner's widget. */
public enum TopUpMethod {
    BANK_RECEIPT, CARD, MPESA, AIRTEL_MONEY
}
