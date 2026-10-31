package com.intact_id.service.kyc;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.service.billing.BillingService;
import com.intact_id.service.webhook.WebhookService;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualReviewService {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationService verificationService;
    private final WebhookService webhookService;
    private final BillingService billingService;

    public Page<VerificationStatusResponseDto> getPendingReviewQueue(Pageable pageable) {
        return verificationRequestRepository
                .findByFilters(null, null, GlobalStatuses.PENDING_APPROVAL.name(),
                        null, null, null, null, null, pageable)
                .map(r -> verificationService.getVerificationStatus(r.getId(), null, r.getEnvironment()));
    }

    @Transactional
    public VerificationStatusResponseDto resolveReview(String verificationId, String decision, String notes, String reviewerEmail) {
        VerificationRequest request = verificationRequestRepository.findById(verificationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Verification not found: " + verificationId));

        if (request.getStatus() != GlobalStatuses.PENDING_APPROVAL) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Verification " + verificationId + " is not pending manual review (status: " + request.getStatus() + ")");
        }

        if (!"approved".equalsIgnoreCase(decision) && !"rejected".equalsIgnoreCase(decision)) {
            throw new ApplicationExceptions.IntactBadRequestException(
                    "Decision must be 'approved' or 'rejected'");
        }

        request.resolveManualReview(decision, notes);
        verificationRequestRepository.save(request);
        billingService.handleVerificationOutcome(request);

        log.info("Manual review resolved for {} by {}: decision={}", verificationId, reviewerEmail, decision);

        webhookService.sendVerificationCompletedWebhook(request.getCompany(), request);

        return verificationService.getVerificationStatus(verificationId, null, request.getEnvironment());
    }
}
