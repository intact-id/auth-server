package com.intact_id.orchestration.kyb;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Who must be checked behind a business: owners at or above ownershipThreshold (percent), and directors when
 * includeDirectors. personWorkflow is the workflow key each person's identity verification runs (FULL_KYC), and
 * autoInvite emails them their link when the business reaches the owner step. companyOverride is true when the
 * company has its own settings rather than the platform default.
 */
public record KybSettings(OwnerCheck ownerCheck, BigDecimal ownershipThreshold, boolean includeDirectors,
                          String personWorkflow, boolean autoInvite, boolean companyOverride) {

    /** Whether a person with this role and share must be checked. */
    public boolean mustVerify(boolean director, BigDecimal ownershipPercent) {
        if (ownerCheck == OwnerCheck.NONE) return false;
        boolean bigOwner = ownershipPercent != null && ownershipPercent.compareTo(ownershipThreshold) >= 0;
        return bigOwner || (director && includeDirectors);
    }

    /** These settings with whatever the workflow's "Directors and owners" step sets itself. */
    public KybSettings withStep(Map<String, Object> step) {
        if (step == null || step.isEmpty()) return this;
        return new KybSettings(
                step.get("ownerCheck") == null ? ownerCheck : OwnerCheck.valueOf(step.get("ownerCheck").toString()),
                step.get("ownershipThreshold") == null ? ownershipThreshold : new BigDecimal(step.get("ownershipThreshold").toString()),
                step.get("includeDirectors") == null ? includeDirectors : Boolean.parseBoolean(step.get("includeDirectors").toString()),
                step.get("personWorkflow") == null || step.get("personWorkflow").toString().isBlank()
                        ? personWorkflow : step.get("personWorkflow").toString().trim(),
                step.get("autoInvite") == null ? autoInvite : Boolean.parseBoolean(step.get("autoInvite").toString()),
                companyOverride);
    }
}
