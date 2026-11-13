package com.intact_id.orchestration.web;

import java.time.LocalDateTime;

/** Screening counts since {@code since}. PEP and adverse media overlap with the verdict counts. */
public record ScreeningSummaryDto(
        LocalDateTime since,
        long total,
        long noHit,
        long potentialMatch,
        long confirmedMatch,
        long skipped,
        long pep,
        long adverseMedia) {
}
