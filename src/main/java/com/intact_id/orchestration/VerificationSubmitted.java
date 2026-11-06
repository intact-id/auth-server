package com.intact_id.orchestration;

/** A verification's documents are stored and it is queued for processing. */
public record VerificationSubmitted(String verificationId, String companyId, String tier) {
}
