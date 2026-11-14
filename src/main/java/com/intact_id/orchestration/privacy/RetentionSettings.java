package com.intact_id.orchestration.privacy;

/**
 * Days after a verification finishes before its documents (files, selfies, liveness frames) are deleted, and before
 * its personal details are erased. null keeps them. companyOverride is true when the company has its own rule.
 */
public record RetentionSettings(Integer documentsDays, Integer personalDataDays, boolean companyOverride) {
}
