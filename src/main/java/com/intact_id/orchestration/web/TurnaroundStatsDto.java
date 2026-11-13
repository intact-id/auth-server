package com.intact_id.orchestration.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Turnaround time (TAT) of verifications submitted between {@code from} and {@code to}, in seconds.
 * Total TAT runs from submission to the final decision, so only finished verifications count towards it.
 * Stage averages count only the verifications that went through that stage; null when none did.
 */
public record TurnaroundStatsDto(
        LocalDateTime from,
        LocalDateTime to,
        long finished,
        long reviewed,
        Double averageSeconds,
        Double medianSeconds,
        Double p90Seconds,
        Double automatedAverageSeconds,
        Double reviewedAverageSeconds,
        Stages stages,
        List<CheckTime> checks,
        List<Day> daily) {

    /** Average seconds spent in each stage. */
    public record Stages(Double queued, Double checks, Double waitingForReviewer, Double underReview) {
    }

    /** How long one kind of check takes. Only runs recorded since per-check timing began count. */
    public record CheckTime(String step, long runs, Double averageMs, Double p90Ms) {
    }

    public record Day(LocalDate date, long finished, Double averageSeconds, Double medianSeconds) {
    }
}
