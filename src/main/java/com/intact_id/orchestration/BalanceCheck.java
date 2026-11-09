package com.intact_id.orchestration;

/**
 * Asked before a verification is processed: may this client be charged for it? Implemented by
 * billing, so orchestration doesn't depend on it.
 */
public interface BalanceCheck {

    HoldDecision check(String clientIdentifier, String verificationId);

    record HoldDecision(boolean hold, String reason) {
        public static final HoldDecision ALLOW = new HoldDecision(false, null);
    }
}
