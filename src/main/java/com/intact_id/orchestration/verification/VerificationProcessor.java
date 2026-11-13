package com.intact_id.orchestration.verification;

import java.util.Set;
import java.util.Optional;
import com.intact_id.workflow.WorkflowRuntime;
import com.intact_id.workflow.WorkflowRun;
import com.intact_id.workflow.WorkflowExecutor;
import com.intact_id.workflow.PinnedWorkflow;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.orchestration.VerificationDocument;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.VerificationOutcome;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.intact_id.orchestration.BalanceCheck;
import com.intact_id.orchestration.BalanceCheck.HoldDecision;
import com.intact_id.orchestration.verification.RateLimitService.LimitExceeded;
import com.intact_id.orchestration.model.VerificationStep;
import com.intact_id.orchestration.model.VerificationDocumentRepository;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.model.VerificationStepRepository;
import com.intact_id.notifications.EmailService;
import com.intact_id.orchestration.events.VerificationStatusChangedData;
import com.intact_id.orchestration.events.VerificationStatusChangedEvent;
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
    private final PlatformTransactionManager transactionManager;
    private final WorkflowRuntime workflowRuntime;
    private final BalanceCheck balanceCheck;

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

        // Wallet balance check — billing implements BalanceCheck by asking billing-service.
        // The local check-and-hold path (BillingService.checkSufficientBalance) is deprecated.
        HoldDecision balanceDecision = balanceCheck.check(
                request.getCompany().getCompanyIdentifierCode(), request.getId(), request.getWorkflowId());
        if (balanceDecision.hold()) {
            request.markAsHeld(balanceDecision.reason());
            verificationRequestRepository.save(request);
            log.info("Verification {} held — {}", request.getId(), balanceDecision.reason());
            return;
        }

        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(request.getId());

        // A workflow run calls only its services' checks; a tier-based run calls them all
        Optional<PinnedWorkflow> workflow = request.getWorkflowId() == null || request.getWorkflowVersion() == null
                ? Optional.empty() : workflowRuntime.find(request.getWorkflowId(), request.getWorkflowVersion());
        Set<String> services = workflow.map(w -> Set.copyOf(w.services())).orElse(null);

        ExternalKycServiceClient.VerificationResult result;
        try {
            result = externalKycServiceClient
                    .performVerification(request.getTier(), request, documents, services)
                    .join();
        } catch (Exception e) {
            log.error("External KYC service call failed for: {}", request.getId(), e);
            String failureReason = "SystemError";
            String errorDetails = e.getMessage();

            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof feign.FeignException feignEx) {
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

            // Transient/connectivity failures (timeouts, connection refused, 5xx) don't mean the
            // verification itself failed — hold it for a human to release once the service is back,
            // instead of auto-retrying (which would re-run paid face/OCR calls) or failing it outright.
            if (isRetryableServiceError(cause)) {
                request.markAsHeld("Transient error from verification service: " + errorDetails);
                verificationRequestRepository.save(request);
                log.warn("Verification {} held — transient service error: {}", request.getId(), errorDetails);
                return;
            }

            markAsFailed(request, failureReason, errorDetails);
            publishStatusChanged(request);
            return;
        }

        if (request.getWorkflowId() != null) {
            applyWorkflow(request, workflow, result);
        }

        for (ExternalKycServiceClient.CheckResult check : result.getChecks()) {
            VerificationStep step = new VerificationStep();
            step.setVerificationRequest(request);
            step.setStepName(check.getStepName());
            step.setStatus(mapStepStatus(check.getStatus()));
            step.setConfidenceScore(check.getConfidenceScore());
            step.setResult(check.getResult());
            step.setExecutedAt(LocalDateTime.now());
            step.setStartedAt(check.getStartedAt());
            step.setDurationMs(check.getDurationMs());
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
            request.markAsManualReview(result.getRiskScore(), reviewReason(result));
            notifyManualReviewRequired(request);
        } else {
            request.markAsCompleted(result.getOverallDecision(), result.getRiskScore());
            if (result.getFailureReason() != null) request.setFailureReason(result.getFailureReason());
            if (result.getErrorDetails() != null) request.setErrorDetails(result.getErrorDetails());
        }

        // Manual-review outcomes aren't final yet; ManualReviewService publishes the outcome
        saveWithOutcome(request, request.getStatus() != GlobalStatuses.PENDING_APPROVAL);
        log.info("Verification completed for: {} decision={}", request.getId(), result.getOverallDecision());
        publishStatusChanged(request);
    }

    private void markAsFailed(VerificationRequest request, String failureReason, String errorDetails) {
        if (shouldReject(failureReason, errorDetails, null)) {
            request.markAsRejected(failureReason, errorDetails, 0.95);
        } else {
            request.markAsFailed(failureReason, errorDetails);
        }
        saveWithOutcome(request, true);
    }

    /** Saves the result and records the outcome event in one transaction, so neither happens without the other. */
    private void saveWithOutcome(VerificationRequest request, boolean finalOutcome) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            verificationRequestRepository.save(request);
            if (finalOutcome) {
                eventPublisher.publishEvent(VerificationOutcome.of(request));
            }
        });
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

    // Network-level failures, 5xx responses, and an open circuit breaker are the service's
    // fault, not the applicant's data — hold for retry. Anything else (4xx, bad response
    // parsing, "selfie not found", etc.) is terminal. See ExternalServiceFailureClassifier.
    private static boolean isRetryableServiceError(Throwable cause) {
        return ExternalServiceFailureClassifier.isTransient(cause);
    }

    /** The workflow, not the fixed check bundle, decides: walk the pinned graph and keep the path for audit. */
    private void applyWorkflow(VerificationRequest request, Optional<PinnedWorkflow> workflow,
                               ExternalKycServiceClient.VerificationResult result) {
        if (workflow.isEmpty()) {
            log.error("Verification {} pins workflow {} v{}, which no longer exists", request.getId(),
                    request.getWorkflowId(), request.getWorkflowVersion());
            result.setOverallDecision("manual_review");
            result.setFailureReason("Workflow version not found");
            return;
        }
        WorkflowRun run = WorkflowExecutor.walk(workflow.get().graph(),
                WorkflowOutcomes.from(result, Set.copyOf(workflow.get().services())), WorkflowOutcomes.startFacts(request));
        request.setWorkflowPath(run.path());
        switch (run.decision()) {
            case APPROVED -> {
                result.setOverallDecision("approved");
                result.setFailureReason(null);
            }
            case REJECTED -> {
                result.setOverallDecision("rejected");
                result.setFailureReason(run.reason());
            }
            case MANUAL_REVIEW -> {
                result.setOverallDecision("manual_review");
                result.setFailureReason(run.reason());
            }
        }
        log.info("Verification {} followed workflow {} v{}: {} ({} steps)", request.getId(), workflow.get().key(),
                workflow.get().version(), run.decision(), run.path().size());
    }

    /**
     * Why a verification went to a human. Workflows state their reason; the fixed check bundle
     * does not, so name the checks that did not pass.
     */
    static String reviewReason(ExternalKycServiceClient.VerificationResult result) {
        if (result.getFailureReason() != null && !result.getFailureReason().isBlank()) {
            return result.getFailureReason();
        }
        List<String> unresolvedChecks = result.getChecks() == null ? List.of() : result.getChecks().stream()
                .filter(check -> !"passed".equalsIgnoreCase(check.getStatus()))
                .map(ExternalKycServiceClient.CheckResult::getStepName)
                .toList();
        return unresolvedChecks.isEmpty()
                ? "Automated checks could not decide"
                : "Checks needing review: " + String.join(", ", unresolvedChecks);
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
