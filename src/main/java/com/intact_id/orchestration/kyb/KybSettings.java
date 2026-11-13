package com.intact_id.orchestration.kyb;

import java.math.BigDecimal;

/**
 * Who must be checked behind a business: owners at or above ownershipThreshold (percent), and directors when
 * includeDirectors. personWorkflow is the workflow key each person's identity verification runs (FULL_KYC).
 * companyOverride is true when the company has its own settings rather than the platform default.
 */
public record KybSettings(OwnerCheck ownerCheck, BigDecimal ownershipThreshold, boolean includeDirectors,
                          String personWorkflow, boolean companyOverride) {

    /** Whether a person with this role and share must be checked. */
    public boolean mustVerify(boolean director, BigDecimal ownershipPercent) {
        if (ownerCheck == OwnerCheck.NONE) return false;
        boolean bigOwner = ownershipPercent != null && ownershipPercent.compareTo(ownershipThreshold) >= 0;
        return bigOwner || (director && includeDirectors);
    }
}
