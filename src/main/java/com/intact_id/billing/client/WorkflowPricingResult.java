package com.intact_id.billing.client;

import java.math.BigDecimal;
import java.util.List;

public record WorkflowPricingResult(String workflowId, String billingProfileId, BigDecimal pricePerCall, String currency,
                                    List<ResolvedServiceCharge> breakdown) {
}
