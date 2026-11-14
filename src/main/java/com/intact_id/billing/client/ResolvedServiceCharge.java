package com.intact_id.billing.client;

import java.math.BigDecimal;

/** billing-service's price for one service for one client; source is GLOBAL or OVERRIDE. */
/** source: OVERRIDE, GLOBAL or CONVERTED (basePrice is then the KES price it was converted from). */
public record ResolvedServiceCharge(String serviceCode, String name, BigDecimal price, String currency, String source,
                                    BigDecimal basePrice) {
}
