package com.intact_id.workflow;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/** Prices workflows from billing's service charges. Implemented by billing, so workflow doesn't depend on it. */
public interface WorkflowPricing {

    /** What a workflow with these services would cost this client; for the builder, stores nothing. */
    PriceQuote quote(String clientIdentifier, Collection<String> serviceCodes);

    /** Prices the workflow and stores it as its per-call price; called when a version is published. */
    PriceQuote price(String workflowId, String clientIdentifier, Collection<String> serviceCodes);

    record PriceQuote(BigDecimal total, String currency, List<ServicePrice> services, List<String> unpriced) {
    }

    record ServicePrice(String code, String name, BigDecimal price, String source) {
    }
}
