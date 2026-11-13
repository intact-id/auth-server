package com.intact_id.orchestration.verification;

import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.model.VerificationStep;
import com.intact_id.orchestration.model.VerificationStepRepository;
import com.intact_id.orchestration.web.VerificationTimelineDto;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A verification's turnaround time, stage by stage: queued (or held) until the checks start, the automated
 * checks, waiting for a reviewer, and the review itself. Each stage is built from the timestamps the
 * verification already records.
 */
@Service
@RequiredArgsConstructor
public class VerificationTimeline {

    private final VerificationRequestRepository requests;
    private final VerificationStepRepository steps;

    @Transactional(readOnly = true)
    public VerificationTimelineDto of(String verificationId) {
        VerificationRequest request = requests.findById(verificationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Verification not found"));
        List<VerificationStep> recorded = steps.findByVerificationRequestIdOrderByCreatedAtAsc(verificationId);
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime submitted = request.getCreatedAt();
        LocalDateTime processingStarted = request.getProcessingStartedAt();
        LocalDateTime checksDone = recorded.stream().map(VerificationStep::getExecutedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime finished = request.getCompletedAt() != null ? request.getCompletedAt() : request.getReviewedAt();

        List<VerificationTimelineDto.Stage> stages = new ArrayList<>();

        if (processingStarted != null) {
            stages.add(stage("queued", "Waiting to start", submitted, processingStarted, now,
                    "Includes any time held for a rate limit, a low balance or a service outage", List.of()));
        }

        LocalDateTime checksStarted = processingStarted != null ? processingStarted : submitted;
        if (checksDone != null || request.getReviewRequestedAt() == null) {
            // Before checks finish: running if nothing has decided yet, otherwise the run failed before writing steps
            LocalDateTime checksEnd = checksDone != null ? checksDone : finished;
            stages.add(stage("checks", "Automated checks", checksStarted, checksEnd, now,
                    processingStarted == null ? "Includes the wait before checks started (not recorded separately)" : null,
                    recorded.stream().map(VerificationTimeline::check).toList()));
        }

        if (request.getReviewRequestedAt() != null) {
            LocalDateTime picked = request.getReviewAssignedAt() != null ? request.getReviewAssignedAt() : request.getReviewedAt();
            stages.add(stage("waiting_for_reviewer", "Waiting for a reviewer", request.getReviewRequestedAt(), picked, now,
                    null, List.of()));
            if (request.getReviewAssignedAt() != null) {
                stages.add(stage("under_review", "Under review", request.getReviewAssignedAt(), request.getReviewedAt(), now,
                        request.getReviewedBy() != null ? "Decided by " + request.getReviewedBy() : null, List.of()));
            }
        }

        return new VerificationTimelineDto(request.getId(), request.getStatus().name(), submitted, finished,
                millis(submitted, finished != null ? finished : now), finished == null, stages);
    }

    /** endedAt null = still in this stage; its duration runs to now. */
    private static VerificationTimelineDto.Stage stage(String key, String label, LocalDateTime startedAt, LocalDateTime endedAt,
                                                      LocalDateTime now, String note, List<VerificationTimelineDto.Check> checks) {
        return new VerificationTimelineDto.Stage(key, label, startedAt, endedAt,
                millis(startedAt, endedAt != null ? endedAt : now), endedAt == null, note, checks);
    }

    private static VerificationTimelineDto.Check check(VerificationStep step) {
        return new VerificationTimelineDto.Check(step.getStepName(), step.getStatus().name(), step.getStartedAt(),
                step.getExecutedAt(), step.getDurationMs());
    }

    private static long millis(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null ? 0 : Math.max(0, Duration.between(from, to).toMillis());
    }
}
