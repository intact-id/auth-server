package com.intact_id.orchestration;

import com.intact_id.constant.enums.GlobalStatuses;

/**
 * A verification reached its final result: completed, rejected or failed. Not published when it
 * goes to manual review; the reviewer's decision publishes it instead.
 */
public record VerificationOutcome(
        String verificationId,
        String companyId,
        String clientIdentifier,
        String tier,
        GlobalStatuses status,
        String overallDecision,
        String failureReason) {

    public static VerificationOutcome of(VerificationRequest request) {
        return new VerificationOutcome(
                request.getId(),
                request.getCompany().getId(),
                request.getCompany().getCompanyIdentifierCode(),
                request.getTier(),
                request.getStatus(),
                request.getOverallDecision(),
                request.getFailureReason());
    }
}
