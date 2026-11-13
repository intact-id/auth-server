package com.intact_id.orchestration.kyb;

/** Where one director or owner is in their check. */
public enum PartyStatus {
    /** The settings don't require this person to be checked */
    NOT_REQUIRED,
    /** Must be checked; nothing has started */
    PENDING,
    /** An invite link was sent; the person hasn't started yet */
    INVITED,
    /** Their identity verification is running */
    VERIFYING,
    PASSED,
    FAILED,
    /** Possible screening match, or their verification needs a person to decide */
    REVIEW;

    public boolean isFinal() {
        return this == NOT_REQUIRED || this == PASSED || this == FAILED || this == REVIEW;
    }
}
