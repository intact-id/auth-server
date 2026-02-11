package com.intact_id.service.kyc;

import com.intact_id.config.VerificationProcessingConfig;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.model.kyc.VerificationStep;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.db.repository.kyc.VerificationRequestRepository;
import com.intact_id.db.repository.kyc.VerificationStepRepository;
import com.intact_id.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationProcessingScheduler {

    private final VerificationRequestRepository verificationRequestRepository;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final VerificationStepRepository verificationStepRepository;
    private final ExternalKycServiceClient externalKycServiceClient;
    private final WebhookService webhookService;
    private final VerificationProcessingConfig config;

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
            webhookService.sendVerificationFailedWebhook(request.getCompany(), request);
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

        // Update verification request with final result
        request.markAsCompleted(result.getOverallDecision(), result.getRiskScore());

        if (result.getFailureReason() != null) {
            request.setFailureReason(result.getFailureReason());
        }
        if (result.getErrorDetails() != null) {
            request.setErrorDetails(result.getErrorDetails());
        }

        verificationRequestRepository.save(request);

        log.info("Verification completed for: {} with decision: {}", request.getId(), result.getOverallDecision());

        // Send webhook notification
        webhookService.sendVerificationCompletedWebhook(request.getCompany(), request);
    }

    private void markAsFailed(VerificationRequest request, String failureReason, String errorDetails) {
        request.markAsFailed(failureReason, errorDetails);
        verificationRequestRepository.save(request);
    }
}
