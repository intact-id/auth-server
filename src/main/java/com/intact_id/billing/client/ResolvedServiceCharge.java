package com.intact_id.billing.client;

import java.math.BigDecimal;

/** billing-service's price for one service for one client; source is GLOBAL or OVERRIDE. */
public record ResolvedServiceCharge(String serviceCode, String name, BigDecimal price, String currency, String source) {
}
