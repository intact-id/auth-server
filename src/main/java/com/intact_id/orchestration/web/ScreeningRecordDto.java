package com.intact_id.orchestration.web;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One sanctions/PEP screening run, as stored on its verification.
 *
 * @param verdict       NO_HIT, POTENTIAL_MATCH, CONFIRMED_MATCH, or SKIPPED when screening could not run
 * @param matches       up to five best matches: entity_id, caption, score, datasets, topics
 * @param skippedReason why screening did not run, when verdict is SKIPPED
 */
public record ScreeningRecordDto(
        String id,
        String verificationId,
        String companyId,
        String companyName,
        String subjectName,
        String subjectType,
        String verdict,
        Integer hits,
        Double topScore,
        boolean pep,
        boolean adverseMedia,
        List<?> matches,
        String skippedReason,
        String verificationStatus,
        String overallDecision,
        LocalDateTime checkedAt) {
}
