package com.intact_id.service.kyc;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.service.billing.BillingService;
import com.intact_id.service.billing.BillingService.InsufficientBalance;
import com.intact_id.service.kyc.RateLimitService.LimitExceeded;
import com.intact_id.db.model.kyc.VerificationStep;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.db.repository.kyc.VerificationStepRepository;
import com.intact_id.service.email.EmailService;
import com.intact_id.service.kyc.events.VerificationStatusChangedData;
import com.intact_id.service.kyc.events.VerificationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationProcessor {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationStepRepository verificationStepRepository;
    private final ExternalKycServiceClient externalKycServiceClient;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final BillingService billingService;

    @Value("${app.support-email:support@intact-id.com}")
    private String supportEmail;

    public void process(VerificationRequest request) {
        log.info("Processing verification: {}", request.getId());

        // Rate limit check — runs before any external calls
        LimitExceeded exceeded = rateLimitService.checkAndConsume(request);
        if (exceeded != null) {
            String reason = String.format("%s rate limit exceeded (%d/%d, resets at %s)",
                    exceeded.window(), exceeded.used(), exceeded.limit(), exceeded.resetAt());
            request.markAsHeld(reason);
            verificationRequestRepository.save(request);
            log.info("Verification {} held — {}", request.getId(), reason);
            return;
        }

        // Wallet balance check — only relevant for PREPAID companies
        InsufficientBalance insufficientBalance = billingService.checkSufficientBalance(request);
        if (insufficientBalance != null) {
            String reason = String.format("insufficient wallet balance (available=%s, required=%s)",
                    insufficientBalance.available(), insufficientBalance.required());
            request.markAsHeld(reason);
            verificationRequestRepository.save(request);
            log.info("Verification {} held — {}", request.getId(), reason);
            return;
        }

        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(request.getId());

        ExternalKycServiceClient.VerificationResult result;
        try {
            result = externalKycServiceClient
                    .performVerification(request.getTier(), request, documents)
                    .join();
        } catch (Exception e) {
            log.error("External KYC service call failed for: {}", request.getId(), e);
            String failureReason = "SystemError";
            String errorDetails = e.getMessage();

            if (e.getCause() instanceof feign.FeignException feignEx) {
                try {
                    String body = feignEx.contentUTF8();
                    if (body != null && !body.isBlank()) {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        var json = mapper.readTree(body);
                        if (json.has("error")) failureReason = json.get("error").asText();
                        if (json.has("message")) errorDetails = json.get("message").asText();
                    }
                } catch (Exception ignored) {}
            }

            markAsFailed(request, failureReason, errorDetails);
            publishStatusChanged(request);
            return;
        }

        for (ExternalKycServiceClient.CheckResult check : result.getChecks()) {
            VerificationStep step = new VerificationStep();
            step.setVerificationRequest(request);
            step.setStepName(check.getStepName());
            step.setStatus(mapStepStatus(check.getStatus()));
            step.setConfidenceScore(check.getConfidenceScore());
            step.setResult(check.getResult());
            step.setExecutedAt(LocalDateTime.now());
            verificationStepRepository.save(step);
        }

        if (result.getExtractedOcrData() != null && result.getOcrDocumentId() != null) {
            verificationDocumentRepository.findById(result.getOcrDocumentId()).ifPresent(doc -> {
                doc.setExtractedData(result.getExtractedOcrData());
                verificationDocumentRepository.save(doc);
            });
        }

        if (shouldReject(result.getFailureReason(), result.getErrorDetails(), result.getOverallDecision())) {
            request.markAsRejected(result.getFailureReason(), result.getErrorDetails(), result.getRiskScore());
        } else if ("manual_review".equals(result.getOverallDecision())) {
            request.markAsManualReview(result.getRiskScore());
            notifyManualReviewRequired(request);
        } else {
            request.markAsCompleted(result.getOverallDecision(), result.getRiskScore());
            if (result.getFailureReason() != null) request.setFailureReason(result.getFailureReason());
            if (result.getErrorDetails() != null) request.setErrorDetails(result.getErrorDetails());
        }

        verificationRequestRepository.save(request);
        log.info("Verification completed for: {} decision={}", request.getId(), result.getOverallDecision());
        publishStatusChanged(request);
    }

    private void markAsFailed(VerificationRequest request, String failureReason, String errorDetails) {
        if (shouldReject(failureReason, errorDetails, null)) {
            request.markAsRejected(failureReason, errorDetails, 0.95);
        } else {
            request.markAsFailed(failureReason, errorDetails);
        }
        verificationRequestRepository.save(request);
    }

    private void notifyManualReviewRequired(VerificationRequest request) {
        try {
            String companyName = request.getCompany() != null ? request.getCompany().getLegalName() : "Unknown";
            emailService.sendSimpleEmail(
                    supportEmail,
                    "[Action Required] Manual KYC Review: " + request.getId(),
                    "A KYC verification requires manual review.\n\n" +
                            "Verification ID: " + request.getId() + "\n" +
                            "Company: " + companyName + "\n" +
                            "Tier: " + request.getTier() + "\n\n" +
                            "Please review at the admin dashboard.");
        } catch (Exception e) {
            log.warn("Failed to send manual review notification for {}: {}", request.getId(), e.getMessage());
        }
    }

    private void publishStatusChanged(VerificationRequest request) {
        eventPublisher.publishEvent(new VerificationStatusChangedEvent(
                this, new VerificationStatusChangedData(
                        request.getId(), request.getCompany().getId(), request.getStatus())));
    }

    private static boolean shouldReject(String failureReason, String errorDetails, String overallDecision) {
        if ("rejected".equalsIgnoreCase(overallDecision)) return true;
        String failure = failureReason == null ? "" : failureReason.toLowerCase(Locale.ROOT);
        String details = errorDetails == null ? "" : errorDetails.toLowerCase(Locale.ROOT);
        return failure.contains("face not detected")
                || failure.contains("facenotdetected")
                || details.contains("face not detected")
                || details.contains("no face detected")
                || details.contains("no face found");
    }

    private static GlobalStatuses mapStepStatus(String status) {
        if ("passed".equalsIgnoreCase(status)) {
            return GlobalStatuses.COMPLETED;
        }
        if ("review".equalsIgnoreCase(status) || "manual_review".equalsIgnoreCase(status)) {
            return GlobalStatuses.PENDING_APPROVAL;
        }
        return GlobalStatuses.FAILED;
    }
}
