package com.intact_id.orchestration.verification;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.web.VerificationStatusResponseDto;
import com.intact_id.orchestration.VerificationOutcome;
import org.springframework.context.ApplicationEventPublisher;
import com.intact_id.orchestration.events.VerificationWebhooks;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualReviewService {

    /** Which part of the review queue to list. */
    public enum QueueFilter { ALL, MINE, UNASSIGNED }

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationService verificationService;
    private final VerificationWebhooks verificationWebhooks;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Verifications waiting for a human, longest-waiting first, each with its review record.
     * companyId scopes every method here: null only for super admins, who may act on any company.
     */
    @Transactional(readOnly = true)
    public Page<VerificationStatusResponseDto> getPendingReviewQueue(String companyId, QueueFilter filter, String reviewer,
                                                                     Pageable pageable) {
        String assignedTo = filter == QueueFilter.MINE ? reviewer : null;
        boolean unassignedOnly = filter == QueueFilter.UNASSIGNED;
        return verificationRequestRepository
                .findReviewQueue(GlobalStatuses.PENDING_APPROVAL, companyId, assignedTo, unassignedOnly, pageable)
                .map(r -> verificationService.withStaffDetails(
                        verificationService.getVerificationStatus(r.getId(), null), false));
    }

    @Transactional
    public VerificationStatusResponseDto resolveReview(String companyId, String verificationId, String decision, String notes,
                                                       String reviewer) {
        if (!"approved".equalsIgnoreCase(decision) && !"rejected".equalsIgnoreCase(decision)) {
            throw new ApplicationExceptions.IntactBadRequestException("Decision must be 'approved' or 'rejected'");
        }
        VerificationRequest request = findPendingReview(companyId, verificationId);

        request.resolveManualReview(decision, blankToNull(notes), reviewer);
        verificationRequestRepository.save(request);
        eventPublisher.publishEvent(VerificationOutcome.of(request));

        log.info("Manual review resolved for {} by {}: decision={}", verificationId, reviewer, decision);

        verificationWebhooks.completed(request);

        return staffView(request);
    }

    /** Takes the case for the given reviewer, including from another reviewer. */
    @Transactional
    public VerificationStatusResponseDto assign(String companyId, String verificationId, String reviewer) {
        VerificationRequest request = findPendingReview(companyId, verificationId);
        String previous = request.getReviewAssignedTo();
        request.assignReviewer(reviewer);
        verificationRequestRepository.save(request);
        log.info("Manual review {} assigned to {} (was {})", verificationId, reviewer, previous);
        return staffView(request);
    }

    @Transactional
    public VerificationStatusResponseDto unassign(String companyId, String verificationId, String reviewer) {
        VerificationRequest request = findPendingReview(companyId, verificationId);
        String previous = request.getReviewAssignedTo();
        request.unassignReviewer();
        verificationRequestRepository.save(request);
        log.info("Manual review {} unassigned by {} (was {})", verificationId, reviewer, previous);
        return staffView(request);
    }

    private VerificationRequest findPendingReview(String companyId, String verificationId) {
        VerificationRequest request = verificationRequestRepository.findById(verificationId)
                // Another company's verification answers the same as a missing one
                .filter(r -> companyId == null || (r.getCompany() != null && companyId.equals(r.getCompany().getId())))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Verification not found: " + verificationId));
        if (request.getStatus() != GlobalStatuses.PENDING_APPROVAL) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Verification " + verificationId + " is not pending manual review (status: " + request.getStatus() + ")");
        }
        return request;
    }

    private VerificationStatusResponseDto staffView(VerificationRequest request) {
        return verificationService.withStaffDetails(
                verificationService.getVerificationStatus(request.getId(), null), true);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
