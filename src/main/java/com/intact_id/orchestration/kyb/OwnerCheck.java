package com.intact_id.orchestration.kyb;

/** How the people behind a business are checked. */
public enum OwnerCheck {
    /** Nobody is checked */
    NONE,
    /** Sanctions and PEP screening only */
    SCREENING,
    /** Each person completes their own identity verification */
    FULL_KYC
}
