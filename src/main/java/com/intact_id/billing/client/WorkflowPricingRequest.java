package com.intact_id.billing.client;

import java.util.List;

public record WorkflowPricingRequest(String clientIdentifier, List<String> serviceCodes) {
}
