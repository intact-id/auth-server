package com.intact_id.orchestration.web;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One verification's turnaround time, stage by stage. A stage still running has no endedAt and its duration
 * counts up to now. Verifications from before stage timing was recorded may lack the queued stage and check times.
 */
public record VerificationTimelineDto(
        String verificationId,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime finishedAt,
        long totalMs,
        boolean running,
        List<Stage> stages) {

    /** key: queued, checks, waiting_for_reviewer or under_review. */
    public record Stage(String key, String label, LocalDateTime startedAt, LocalDateTime endedAt, long durationMs,
                        boolean running, String note, List<Check> checks) {
    }

    /** durationMs null: recorded before per-check timing began. */
    public record Check(String step, String status, LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs) {
    }
}
