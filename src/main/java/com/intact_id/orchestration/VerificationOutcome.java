package com.intact_id.orchestration;

import com.intact_id.constant.enums.GlobalStatuses;

import java.util.List;

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
        String failureReason,
        // Billing charges this workflow's price; null for tier-based runs
        String workflowId,
        // Catalog codes of the checks a workflow run reached; null when unknown (tier runs, failures)
        List<String> checksRun) {

    /** Key in the verification's metadata where the processor records the checks a workflow run reached. */
    public static final String CHECKS_RUN = "checksRun";

    public static VerificationOutcome of(VerificationRequest request) {
        return new VerificationOutcome(
                request.getId(),
                request.getCompany().getId(),
                request.getCompany().getCompanyIdentifierCode(),
                request.getTier(),
                request.getStatus(),
                request.getOverallDecision(),
                request.getFailureReason(),
                request.getWorkflowId(),
                checksRun(request));
    }

    private static List<String> checksRun(VerificationRequest request) {
        Object recorded = request.getMetadata() == null ? null : request.getMetadata().get(CHECKS_RUN);
        return recorded instanceof List<?> codes ? codes.stream().map(String::valueOf).toList() : null;
    }
}
