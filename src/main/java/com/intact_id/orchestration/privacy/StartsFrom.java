package com.intact_id.orchestration.privacy;

/** When a retention period starts counting. */
public enum StartsFrom {
    /** When the verification finished */
    FINISHED,
    /** When the merchant says the customer relationship ended; nothing is due before that (AML record keeping) */
    RELATIONSHIP_END
}
