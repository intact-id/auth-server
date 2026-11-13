package com.intact_id.orchestration.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Verification activity between {@code from} and {@code to}, with the same counts for the period before it.
 * passRate is approved out of those decided (approved + rejected), 0–100; null when nothing was decided.
 */
public record VerificationStatsDto(
        LocalDateTime from,
        LocalDateTime to,
        Counts current,
        Counts previous,
        List<Day> daily,
        List<Slice> byType,
        List<CountrySlice> byCountry,
        List<CheckSlice> checks) {

    public record Counts(long total, long approved, long rejected, long inReview, long inProgress, long failed,
                         Double passRate) {
    }

    public record Day(LocalDate date, long total, long approved, long rejected, long inReview) {
    }

    public record Slice(String label, long count) {
    }

    public record CountrySlice(String country, long total, long approved, long rejected) {
    }

    /** How one kind of check came out. failed includes checks that could not run. */
    public record CheckSlice(String step, long total, long passed, long review, long failed) {
    }
}
