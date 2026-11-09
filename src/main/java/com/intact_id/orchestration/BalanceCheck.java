package com.intact_id.orchestration;

/**
 * Asked before a verification is processed: may this client be charged for it? Implemented by
 * billing, so orchestration doesn't depend on it.
 */
public interface BalanceCheck {

    /** workflowId: the run's workflow, priced per workflow in billing; null = the client's default price. */
    HoldDecision check(String clientIdentifier, String verificationId, String workflowId);

    record HoldDecision(boolean hold, String reason) {
        public static final HoldDecision ALLOW = new HoldDecision(false, null);
    }
}
