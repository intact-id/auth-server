package com.intact_id.orchestration.privacy;

/**
 * Kinds of applicant data, each kept for its own period. Each one's erasure includes the ones before it:
 * erasing personal data also deletes documents and biometrics; purging the record erases everything.
 */
public enum RetentionCategory {
    /** Selfies and liveness frames */
    BIOMETRICS,
    /** ID documents, proof of address and business documents */
    DOCUMENTS,
    /** Names, ID numbers, dates of birth, contact details, what was read from documents, directors' details */
    PERSONAL_DATA,
    /** The verification itself: its checks, decision trail, people and webhook history. A bare row remains. */
    RECORD;

    /** Whether erasing this category also erases {@code other}. */
    public boolean includes(RetentionCategory other) {
        return other.ordinal() <= ordinal();
    }
}
