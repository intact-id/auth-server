package com.intact_id.service.kyc;

import com.intact_id.config.VerificationProcessingConfig;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationProcessingScheduler {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationStepRepository verificationStepRepository;
    private final ExternalKycServiceClient externalKycServiceClient;
    private final ApplicationEventPublisher eventPublisher;
    private final VerificationProcessingConfig config;
    private final EmailService emailService;

    @Value("${app.support-email:support@intact-id.com}")
    private String supportEmail;

    @Scheduled(fixedDelayString = "${verification.processing.poll-interval-ms:30000}")
    public void processSubmittedVerifications() {
        if (!config.isEnabled()) {
            return;
        }

        try {
            List<VerificationRequest> pending = verificationRequestRepository
                    .findByStatusOrderByCreatedAtAsc(
                            GlobalStatuses.SUBMITTED,
                            PageRequest.of(0, config.getBatchSize()));

            if (pending.isEmpty()) {
                return;
            }

            log.info("Found {} submitted verification(s) to process", pending.size());

            for (int i = 0; i < pending.size(); i++) {
                VerificationRequest request = pending.get(i);
                try {
                    processSingleVerification(request);
                } catch (Exception e) {
                    log.error("Verification processing failed for: {}", request.getId(), e);
                    markAsFailed(request, "SystemError", e.getMessage());
                    publishStatusChanged(request);
                }

                // Delay between calls (skip after last request)
                if (i < pending.size() - 1 && config.getDelayBetweenCallsMs() > 0) {
                    Thread.sleep(config.getDelayBetweenCallsMs());
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Verification processing interrupted");
        } catch (Exception e) {
            log.error("Error in verification processing scheduler", e);
        }
    }

    @Transactional
    protected void processSingleVerification(VerificationRequest request) {
        log.info("Processing verification: {}", request.getId());

        // Mark as PROCESSING
        request.markAsProcessing();
        verificationRequestRepository.save(request);

        // Fetch documents from DB
        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(request.getId());

        // Call external KYC service (blocking)
        ExternalKycServiceClient.VerificationResult result;
        try {
            result = externalKycServiceClient
                    .performVerification(request.getTier(), request, documents)
                    .join();
        } catch (Exception e) {
            log.error("External KYC service call failed for: {}", request.getId(), e);

            String failureReason = "SystemError";
            String errorDetails = e.getMessage();

            if (e.getCause() != null && e.getCause() instanceof feign.FeignException) {
                feign.FeignException feignException = (feign.FeignException) e.getCause();
                try {
                    String responseBody = feignException.contentUTF8();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode errorJson = mapper.readTree(responseBody);
                        if (errorJson.has("error")) {
                            failureReason = errorJson.get("error").asText();
                        }
                        if (errorJson.has("message")) {
                            errorDetails = errorJson.get("message").asText();
                        }
                    }
                } catch (Exception parseEx) {
                    log.warn("Failed to parse error response", parseEx);
                }
            }

            markAsFailed(request, failureReason, errorDetails);
            publishStatusChanged(request);
            return;
        }

        // Save verification steps
        for (ExternalKycServiceClient.CheckResult check : result.getChecks()) {
            VerificationStep step = new VerificationStep();
            step.setVerificationRequest(request);
            step.setStepName(check.getStepName());
            step.setStatus("passed".equals(check.getStatus()) ? GlobalStatuses.COMPLETED : GlobalStatuses.FAILED);
            step.setConfidenceScore(check.getConfidenceScore());
            step.setResult(check.getResult());
            step.setExecutedAt(LocalDateTime.now());
            verificationStepRepository.save(step);
        }

        // Persist OCR extracted data onto the id_front document
        if (result.getExtractedOcrData() != null && result.getOcrDocumentId() != null) {
            verificationDocumentRepository.findById(result.getOcrDocumentId()).ifPresent(doc -> {
                doc.setExtractedData(result.getExtractedOcrData());
                verificationDocumentRepository.save(doc);
            });
        }

        // Map outcome to final status
        if (shouldRejectVerification(result.getFailureReason(), result.getErrorDetails(), result.getOverallDecision())) {
            request.markAsRejected(result.getFailureReason(), result.getErrorDetails(), result.getRiskScore());
        } else if ("manual_review".equals(result.getOverallDecision())) {
            request.markAsManualReview(result.getRiskScore());
            notifyManualReviewRequired(request);
        } else {
            request.markAsCompleted(result.getOverallDecision(), result.getRiskScore());

            if (result.getFailureReason() != null) {
                request.setFailureReason(result.getFailureReason());
            }
            if (result.getErrorDetails() != null) {
                request.setErrorDetails(result.getErrorDetails());
            }
        }

        verificationRequestRepository.save(request);

        log.info("Verification completed for: {} with decision: {}", request.getId(), result.getOverallDecision());

        publishStatusChanged(request);
    }

    private void publishStatusChanged(VerificationRequest request) {
        eventPublisher.publishEvent(new VerificationStatusChangedEvent(
                this, new VerificationStatusChangedData(
                        request.getId(), request.getCompany().getId(), request.getStatus())));
    }

    private void markAsFailed(VerificationRequest request, String failureReason, String errorDetails) {
        if (shouldRejectVerification(failureReason, errorDetails, null)) {
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

    private boolean shouldRejectVerification(String failureReason, String errorDetails, String overallDecision) {
        if ("rejected".equalsIgnoreCase(overallDecision)) {
            return true;
        }

        String failure = failureReason == null ? "" : failureReason.toLowerCase(Locale.ROOT);
        String details = errorDetails == null ? "" : errorDetails.toLowerCase(Locale.ROOT);

        return failure.contains("face not detected")
                || failure.contains("facenotdetected")
                || details.contains("face not detected")
                || details.contains("no face detected")
                || details.contains("no face found");
    }
}
