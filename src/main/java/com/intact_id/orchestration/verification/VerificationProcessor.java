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
import com.intact_id.accounts.NotificationService;
import com.intact_id.checks.BusinessRegistry;
import com.intact_id.orchestration.kyb.KybParties;
import com.intact_id.orchestration.events.VerificationStatusChangedData;
import com.intact_id.orchestration.events.VerificationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashSet;
import java.util.ArrayList;
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
    private final NotificationService notificationService;
    private final BusinessRegistry businessRegistry;
    private final KybParties kybParties;

    @Value("${app.support-email:support@intact-id.com}")
    private String supportEmail;

    public void process(VerificationRequest request) {
        log.info("Processing verification: {}", request.getId());
        // A business that waited for its directors and owners already passed the rate limit and balance checks
        boolean resuming = request.getMetadata() != null && request.getMetadata().remove(RESUMED) != null;

        // Rate limit check — runs before any external calls
        LimitExceeded exceeded = resuming ? null : rateLimitService.checkAndConsume(request);
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
        HoldDecision balanceDecision = resuming ? HoldDecision.ALLOW : balanceCheck.check(
                request.getCompany().getCompanyIdentifierCode(), request.getId(), request.getWorkflowId());
        if (balanceDecision.hold()) {
            request.markAsHeld(balanceDecision.reason());
            verificationRequestRepository.save(request);
            log.info("Verification {} held — {}", request.getId(), balanceDecision.reason());
            return;
        }

        List<VerificationDocument> documents = verificationDocumentRepository
                .findByVerificationRequestId(request.getId());

        // A workflow run calls only the checks its path reaches; a tier-based run calls them all
        Optional<PinnedWorkflow> workflow = request.getWorkflowId() == null || request.getWorkflowVersion() == null
                ? Optional.empty() : workflowRuntime.find(request.getWorkflowId(), request.getWorkflowVersion());

        // Checks saved by an earlier pass that waited (business verifications waiting for their people)
        List<VerificationStep> earlier = workflow.isPresent()
                ? verificationStepRepository.findByVerificationRequestIdOrderByCreatedAtAsc(request.getId()) : List.of();

        ExternalKycServiceClient.VerificationResult result;
        try {
            result = workflow.isPresent()
                    ? runReachedChecks(request, documents, workflow.get(), earlier)
                    : externalKycServiceClient.performVerification(request.getTier(), request, documents, null).join();
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

        if (WAITING.equals(result.getStatus())) {
            saveNewSteps(request, result, earlier.size());
            request.getMetadata().put(KybParties.WAITING, KybParties.WAITING_FOR_PEOPLE);
            request.setStatus(GlobalStatuses.PENDING);
            verificationRequestRepository.save(request);
            log.info("Verification {} waits for its directors and owners to finish verification", request.getId());
            publishStatusChanged(request);
            return;
        }

        if (request.getWorkflowId() != null) {
            applyWorkflow(request, workflow, result);
        }

        saveNewSteps(request, result, earlier.size());

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
        String companyName = request.getCompany() != null ? request.getCompany().getLegalName() : "Unknown";
        notificationService.notifyStaff("Verification needs manual review",
                companyName + ": " + Optional.ofNullable(request.getReviewReason()).orElse("a reviewer must decide") + ".",
                "WARNING", "/dashboard/verifications/" + request.getId());
        try {
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

    /** Saves the checks this pass ran; the first `alreadySaved` came from an earlier pass. */
    private void saveNewSteps(VerificationRequest request, ExternalKycServiceClient.VerificationResult result, int alreadySaved) {
        List<ExternalKycServiceClient.CheckResult> checks = result.getChecks();
        for (ExternalKycServiceClient.CheckResult check : checks.subList(Math.min(alreadySaved, checks.size()), checks.size())) {
            VerificationStep step = new VerificationStep();
            step.setVerificationRequest(request);
            step.setStepName(check.getStepName());
            step.setStatus(mapStepStatus(check.getStatus()));
            step.setCheckStatus(check.getStatus());
            step.setConfidenceScore(check.getConfidenceScore());
            step.setResult(check.getResult());
            step.setExecutedAt(LocalDateTime.now());
            step.setStartedAt(check.getStartedAt());
            step.setDurationMs(check.getDurationMs());
            verificationStepRepository.save(step);
        }
    }

    /** A check saved by an earlier pass, as the run loop sees it. */
    private static ExternalKycServiceClient.CheckResult fromStep(VerificationStep step) {
        String status = step.getCheckStatus() != null ? step.getCheckStatus()
                : step.getStatus() == GlobalStatuses.COMPLETED ? "passed"
                : step.getStatus() == GlobalStatuses.PENDING_APPROVAL ? "review" : "failed";
        return ExternalKycServiceClient.CheckResult.builder().stepName(step.getStepName()).status(status)
                .confidenceScore(step.getConfidenceScore()).result(step.getResult())
                .startedAt(step.getStartedAt()).durationMs(step.getDurationMs()).build();
    }

    /** The registry module's answer as a check: not registered fails, any status but ACTIVE goes to review. */
    private ExternalKycServiceClient.CheckResult registryCheck(VerificationRequest request) {
        Map<String, Object> business = Optional.ofNullable(request.getCustomerData()).orElse(Map.of());
        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.nanoTime();
        BusinessRegistry.Lookup lookup = businessRegistry.lookup(text(business.get("registrationNumber")),
                text(business.get("companyName")), text(business.get("country")));
        Map<String, Object> facts = new HashMap<>();
        facts.put("registered", lookup.registered());
        if (lookup.status() != null) facts.put("status", lookup.status());
        facts.put("source", lookup.source());
        String status = !lookup.registered() ? "failed" : "ACTIVE".equalsIgnoreCase(lookup.status()) ? "passed" : "review";
        return ExternalKycServiceClient.CheckResult.builder().stepName(REGISTRY_STEP).status(status).result(facts)
                .startedAt(startedAt).durationMs((System.nanoTime() - start) / 1_000_000).build();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    static final String WAITING = "waiting";
    static final String REGISTRY_STEP = "business_registry";
    public static final String RESUMED = "resumed";

    /**
     * Runs the workflow's checks one call at a time, in the order its path reaches them, so checks after an
     * early rejection (or on a branch not taken) are never called. Document check and data cross-check share
     * one OCR call, so they run together. The business registry and the directors-and-owners check run here.
     * Returns status "waiting" when directors or owners still have verifications running.
     */
    private ExternalKycServiceClient.VerificationResult runReachedChecks(VerificationRequest request,
                                                                         List<VerificationDocument> documents,
                                                                         PinnedWorkflow workflow,
                                                                         List<VerificationStep> earlier) {
        Set<String> inWorkflow = Set.copyOf(workflow.services());
        Map<String, Object> startFacts = WorkflowOutcomes.startFacts(request);
        ExternalKycServiceClient.VerificationResult combined = ExternalKycServiceClient.VerificationResult.builder()
                .verificationId(request.getId()).status("completed")
                .checks(new ArrayList<>(earlier.stream().map(VerificationProcessor::fromStep).toList()))
                .overallDecision("approved").riskScore(0.1).build();
        Set<String> ran = new HashSet<>();
        while (true) {
            Optional<String> next = WorkflowExecutor.nextService(workflow.graph(),
                    WorkflowOutcomes.from(combined, inWorkflow), startFacts);
            // Done, or a check that ran but left no result (the walk then stops at manual review)
            if (next.isEmpty() || ran.contains(next.get())) return combined;

            Set<String> call = OCR_SERVICES.contains(next.get())
                    ? OCR_SERVICES.stream().filter(inWorkflow::contains).collect(Collectors.toSet())
                    : Set.of(next.get());
            ExternalKycServiceClient.VerificationResult part;
            if (BUSINESS_REGISTRY.equals(next.get())) {
                part = single(request, registryCheck(request));
            } else if (OWNER_VERIFICATION.equals(next.get())) {
                Optional<ExternalKycServiceClient.CheckResult> owners = kybParties.check(request);
                if (owners.isEmpty()) {
                    combined.setStatus(WAITING);
                    return combined;
                }
                part = single(request, owners.get());
            } else {
                part = externalKycServiceClient.performVerification(request.getTier(), request, documents, call).join();
            }
            ran.addAll(call);
            combined = combine(combined, part);
            if ("failed".equals(part.getStatus())) return combined;
        }
    }

    private static final Set<String> OCR_SERVICES = Set.of("DOCUMENT_CHECK", "DATA_CROSSCHECK");
    private static final String BUSINESS_REGISTRY = "BUSINESS_REGISTRY";
    private static final String OWNER_VERIFICATION = "OWNER_VERIFICATION";

    private static ExternalKycServiceClient.VerificationResult single(VerificationRequest request,
                                                                      ExternalKycServiceClient.CheckResult check) {
        String decision = switch (check.getStatus()) {
            case "passed" -> "approved";
            case "failed" -> "rejected";
            default -> "manual_review";
        };
        return ExternalKycServiceClient.VerificationResult.builder().verificationId(request.getId()).status("completed")
                .checks(new ArrayList<>(List.of(check))).overallDecision(decision).riskScore("approved".equals(decision) ? 0.1 : 0.5)
                .build();
    }
    private static final List<String> DECISION_ORDER = List.of("approved", "manual_review", "rejected", "error");

    /** Adds one call's checks to what has run so far; the worse decision and higher risk win. */
    private static ExternalKycServiceClient.VerificationResult combine(ExternalKycServiceClient.VerificationResult so,
                                                                       ExternalKycServiceClient.VerificationResult part) {
        List<ExternalKycServiceClient.CheckResult> checks = new ArrayList<>(so.getChecks());
        if (part.getChecks() != null) checks.addAll(part.getChecks());
        String decision = DECISION_ORDER.indexOf(part.getOverallDecision()) > DECISION_ORDER.indexOf(so.getOverallDecision())
                ? part.getOverallDecision() : so.getOverallDecision();
        return ExternalKycServiceClient.VerificationResult.builder()
                .verificationId(so.getVerificationId())
                .status("failed".equals(part.getStatus()) ? part.getStatus() : so.getStatus())
                .checks(checks)
                .overallDecision(decision)
                .riskScore(Math.max(Optional.ofNullable(so.getRiskScore()).orElse(0.0), Optional.ofNullable(part.getRiskScore()).orElse(0.0)))
                .failureReason(so.getFailureReason() != null ? so.getFailureReason() : part.getFailureReason())
                .errorDetails(so.getErrorDetails() != null ? so.getErrorDetails() : part.getErrorDetails())
                .extractedOcrData(so.getExtractedOcrData() != null ? so.getExtractedOcrData() : part.getExtractedOcrData())
                .ocrDocumentId(so.getOcrDocumentId() != null ? so.getOcrDocumentId() : part.getOcrDocumentId())
                .build();
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
