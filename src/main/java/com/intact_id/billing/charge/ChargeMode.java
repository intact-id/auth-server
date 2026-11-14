package com.intact_id.billing.charge;

/** How a workflow run is charged. Tier-based runs always use the company's per-call price. */
public enum ChargeMode {
    /** The workflow's published price, whichever checks ran */
    WORKFLOW_PRICE,
    /** Only the checks the run reached, at today's service prices */
    CHECKS_RUN
}
