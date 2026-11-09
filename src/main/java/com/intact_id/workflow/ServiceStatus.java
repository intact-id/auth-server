package com.intact_id.workflow;

/**
 * Lifecycle of a catalog service. Only AVAILABLE services can be added to workflows; DEPRECATED ones
 * keep running in workflows that already use them; RETIRED ones can no longer be published.
 */
public enum ServiceStatus {
    IN_DEVELOPMENT,
    AVAILABLE,
    DEPRECATED,
    RETIRED
}
