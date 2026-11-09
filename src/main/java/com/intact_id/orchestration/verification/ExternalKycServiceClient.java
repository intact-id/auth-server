package com.intact_id.orchestration.verification;

import com.intact_id.checks.FaceScreeningClient;
import com.intact_id.checks.OcrScreeningClient;
import com.intact_id.orchestration.config.ResilienceConfig;
import com.intact_id.orchestration.VerificationDocument;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.model.VerificationDocumentRepository;
import com.intact_id.checks.FaceVerificationResponse;
import com.intact_id.checks.LivenessResponse;
import com.intact_id.checks.OcrExtractionResponse;
import com.intact_id.orchestration.verification.OcrValidationService.ValidationDecision;
import com.intact_id.orchestration.verification.OcrValidationService.ValidationOutcome;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalKycServiceClient {

    private final FaceScreeningClient faceScreeningClient;
    private final OcrScreeningClient ocrScreeningClient;
    private final OcrValidationService ocrValidationService;
    private final VerificationDocumentRepository verificationDocumentRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final VerificationDocumentFiles verificationDocumentFiles;

    @Value("${face-service.api-key}")
    private String faceApiKey;

    @Value("${face-service.api-secret}")
    private String faceApiSecret;

    @Value("${ocr-service.api-key}")
    private String ocrApiKey;

    @Value("${ocr-service.api-secret}")
    private String ocrApiSecret;

    public CompletableFuture<VerificationResult> performVerification(
            String tier,
            VerificationRequest request,
            List<VerificationDocument> documents) {
        return performVerification(tier, request, documents, null);
    }

    /**
     * services: catalog codes the run's workflow uses; only their checks are called (null = all, tier-based).
     * Face match runs for FACE_MATCH, liveness for LIVENESS, OCR for DOCUMENT_CHECK or DATA_CROSSCHECK.
     */
    public CompletableFuture<VerificationResult> performVerification(
            String tier,
            VerificationRequest request,
            List<VerificationDocument> documents,
            Set<String> services) {

        boolean runFace = services == null || services.contains("FACE_MATCH");
        boolean runLiveness = services == null || services.contains("LIVENESS");
        boolean runOcr = services == null || services.contains("DOCUMENT_CHECK") || services.contains("DATA_CROSSCHECK");
        log.info("Starting verification for tier={} id={} face={} liveness={} ocr={}", tier, request.getId(),
                runFace, runLiveness, runOcr);

        return CompletableFuture.supplyAsync(() -> {
            try {
                VerificationDocument selfieDoc = documents.stream()
                        .filter(d -> "selfie".equals(d.getDocumentType()))
                        .findFirst()
                        .orElse(null);
                if (selfieDoc == null && (runFace || runLiveness)) {
                    throw new RuntimeException("Selfie not found");
                }

                VerificationDocument idDoc = documents.stream()
                        .filter(d -> "id_front".equals(d.getDocumentType())
                                || "id_card".equals(d.getDocumentType()))
                        .findFirst()
                        .orElse(null);
                if (idDoc == null && (runFace || runOcr)) {
                    throw new RuntimeException("ID document not found");
                }

                // Active-liveness frames from the widget (liveness_{index}_{label}), in capture order.
                // Absent for API submissions with a single selfie — those keep the photo check.
                List<VerificationDocument> livenessDocs = documents.stream()
                        .filter(d -> d.getDocumentType() != null
                                && d.getDocumentType().startsWith(VerificationService.LIVENESS_DOC_PREFIX))
                        .sorted(Comparator.comparingInt(ExternalKycServiceClient::livenessFrameIndex))
                        .toList();

                // Each service call gets its own MultipartFile instance to avoid shared stream state
                MultipartFile selfieForFace = runFace ? createMultipartFile(selfieDoc) : null;
                MultipartFile selfieForLiveness = runLiveness ? createMultipartFile(selfieDoc) : null;
                MultipartFile idFileForFace = runFace ? createMultipartFile(idDoc) : null;
                MultipartFile idFileForOcr = runOcr ? createMultipartFile(idDoc) : null;
                MultipartFile[] livenessFrames = new MultipartFile[livenessDocs.size()];
                for (int i = 0; i < livenessDocs.size(); i++)
                    livenessFrames[i] = createMultipartFile(livenessDocs.get(i));
                String livenessLabels = livenessDocs.stream()
                        .map(ExternalKycServiceClient::livenessFrameLabel)
                        .collect(java.util.stream.Collectors.joining(","));

                CircuitBreaker faceBreaker = circuitBreakerRegistry.circuitBreaker(ResilienceConfig.FACE_SERVICE);
                CircuitBreaker ocrBreaker = circuitBreakerRegistry.circuitBreaker(ResilienceConfig.OCR_SERVICE);

                // Face match — critical, failure propagates
                CompletableFuture<FaceVerificationResponse> faceFuture = !runFace ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.supplyAsync(CircuitBreaker.decorateSupplier(faceBreaker, () -> faceScreeningClient.verifyFace(
                                faceApiKey, faceApiSecret, selfieForFace, idFileForFace)));

                // Liveness — degrades to manual_review if service is down (or breaker is open)
                CompletableFuture<LivenessResponse> livenessFuture = !runLiveness ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.supplyAsync(CircuitBreaker.decorateSupplier(faceBreaker, () -> livenessFrames.length > 0
                                ? faceScreeningClient.checkLivenessSequence(faceApiKey, faceApiSecret, livenessFrames, livenessLabels)
                                : faceScreeningClient.checkLiveness(faceApiKey, faceApiSecret, selfieForLiveness)))
                        .exceptionally(ex -> {
                            log.warn("Liveness check unavailable for {}: {}", request.getId(), ex.getMessage());
                            return null;
                        });

                // OCR extraction — degrades to manual_review if service is down (or breaker is open)
                CompletableFuture<OcrExtractionResponse> ocrFuture = !runOcr ? CompletableFuture.completedFuture(null)
                        : CompletableFuture.supplyAsync(CircuitBreaker.decorateSupplier(ocrBreaker, () -> ocrScreeningClient.extractDocument(
                                ocrApiKey, ocrApiSecret, idFileForOcr)))
                        .exceptionally(ex -> {
                            log.warn("OCR service unavailable for {}: {}", request.getId(), ex.getMessage());
                            return null;
                        });

                CompletableFuture.allOf(faceFuture, livenessFuture, ocrFuture).join();

                List<CheckResult> checks = new ArrayList<>();
                String overallDecision = "approved";
                double riskScore = 0.1;

                // --- Face match (critical) ---
                FaceVerificationResponse faceResponse = faceFuture.join();
                if (faceResponse != null) {
                boolean faceManualReview = isManualReview(faceResponse);
                String faceStatus = faceResponse.isVerified()
                        ? "passed"
                        : faceManualReview ? "review" : "failed";
                checks.add(CheckResult.builder()
                        .stepName("face_match")
                        .status(faceStatus)
                        .confidenceScore(1.0 - faceResponse.getDistance())
                        .result(Map.of(
                                "match_score", 1.0 - faceResponse.getDistance(),
                                "verified", faceResponse.isVerified(),
                                "distance", faceResponse.getDistance(),
                                "threshold", faceResponse.getThreshold(),
                                "similarity_metric", faceResponse.getSimilarityMetric(),
                                "review_status", faceResponse.getReviewStatus() != null ? faceResponse.getReviewStatus() : "",
                                "review_reason", faceResponse.getReviewReason() != null ? faceResponse.getReviewReason() : ""))
                        .build());

                if (faceResponse.isVerified()) {
                    // no-op
                } else if (faceManualReview) {
                    overallDecision = "manual_review";
                    riskScore = 0.5;
                } else {
                    overallDecision = "rejected";
                    riskScore = 0.9;
                }
                }

                // --- Liveness (null = service was down → manual review) ---
                LivenessResponse livenessResponse = livenessFuture.join();
                if (!runLiveness) {
                    // Not part of this run's workflow
                } else if (livenessResponse != null) {
                    checks.add(CheckResult.builder()
                            .stepName("liveness_detection")
                            .status(livenessResponse.isPassed() ? "passed" : "failed")
                            .confidenceScore(livenessResponse.getLivenessScore() / 100.0)
                            .result(Map.of(
                                    "liveness_score", livenessResponse.getLivenessScore(),
                                    "passed", livenessResponse.isPassed(),
                                    "checks", livenessResponse.getChecks() != null ? livenessResponse.getChecks() : Map.of(),
                                    "issues", livenessResponse.getIssues() != null ? livenessResponse.getIssues() : List.of()))
                            .build());

                    if (!livenessResponse.isPassed() && !"rejected".equals(overallDecision)) {
                        overallDecision = "rejected";
                        riskScore = 0.9;
                    }
                } else {
                    checks.add(CheckResult.builder()
                            .stepName("liveness_detection")
                            .status("skipped")
                            .confidenceScore(0.0)
                            .result(Map.of("reason", "liveness_service_unavailable"))
                            .build());
                    if ("approved".equals(overallDecision)) {
                        overallDecision = "manual_review";
                        riskScore = 0.5;
                    }
                }

                // --- OCR validation (null = service was down → OcrValidationService handles it) ---
                OcrExtractionResponse ocrResponse = ocrFuture.join();
                if (runOcr) {
                ValidationOutcome ocrOutcome = ocrValidationService.validate(
                        ocrResponse, request.getCustomerData(), tier);
                checks.add(ocrOutcome.checkResult());

                if (ocrOutcome.decision() == ValidationDecision.REJECTED && !"rejected".equals(overallDecision)) {
                    overallDecision = "rejected";
                    riskScore = 0.9;
                } else if (ocrOutcome.decision() == ValidationDecision.MANUAL_REVIEW
                        && "approved".equals(overallDecision)) {
                    overallDecision = "manual_review";
                    riskScore = 0.5;
                }
                }

                // Build extracted OCR data map for persistence on the id_front document
                Map<String, Object> extractedOcrData = buildExtractedOcrData(ocrResponse);

                log.info("Verification completed for {}: decision={} risk={}", request.getId(), overallDecision, riskScore);

                return VerificationResult.builder()
                        .verificationId(request.getId())
                        .status("completed")
                        .checks(checks)
                        .overallDecision(overallDecision)
                        .riskScore(riskScore)
                        .extractedOcrData(extractedOcrData)
                        .ocrDocumentId(idDoc != null ? idDoc.getId() : null)
                        .build();

            } catch (Exception e) {
                log.error("Verification failed for {}", request.getId(), e);

                // Transient failures (timeouts, 5xx, open circuit breaker) must NOT be
                // swallowed into a normal "completed" result here — that would bypass
                // VerificationProcessor's retry/ON_HOLD handling entirely and let the
                // request get marked COMPLETED with overallDecision="error". Rethrow so
                // it surfaces as an exceptional completion and reaches that handling.
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (ExternalServiceFailureClassifier.isTransient(cause)) {
                    throw new java.util.concurrent.CompletionException(cause);
                }

                return buildErrorResult(request.getId(), e);
            }
        });
    }

    private VerificationResult buildErrorResult(String verificationId, Exception e) {
        String failureReason = "SystemError";
        String errorMessage = e.getMessage();

        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof feign.FeignException feignEx) {
            try {
                String body = feignEx.contentUTF8();
                if (body != null && !body.isBlank()) {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var json = mapper.readTree(body);
                    if (json.has("error")) failureReason = json.get("error").asText();
                    if (json.has("message")) errorMessage = json.get("message").asText();
                }
            } catch (Exception ignored) {}
        }

        return VerificationResult.builder()
                .verificationId(verificationId)
                .status("failed")
                .checks(List.of(CheckResult.builder()
                        .stepName("system_check")
                        .status("failed")
                        .errorMessage(errorMessage)
                        .build()))
                .overallDecision("error")
                .failureReason(failureReason)
                .errorDetails(errorMessage)
                .build();
    }

    private static Map<String, Object> buildExtractedOcrData(OcrExtractionResponse ocr) {
        if (ocr == null) return null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("document_type", ocr.getDocumentType());
        data.put("document_type_confidence", ocr.getDocumentTypeConfidence());
        data.put("country_iso3", ocr.getCountryIso3());
        data.put("overall_confidence", ocr.getOverallConfidence());
        if (ocr.getFields() != null) {
            OcrExtractionResponse.OcrFields f = ocr.getFields();
            data.put("full_name", f.getFullName());
            data.put("id_number", f.getIdNumber());
            data.put("date_of_birth", f.getDateOfBirth());
            data.put("nationality", f.getNationality());
            data.put("expiry_date", f.getExpiryDate());
            data.put("date_of_issue", f.getDateOfIssue());
            data.put("sex", f.getSex());
            data.put("place_of_birth", f.getPlaceOfBirth());
        }
        return data;
    }

    private MultipartFile createMultipartFile(VerificationDocument doc) {
        return verificationDocumentFiles.asMultipartFile(doc);
    }

    /** liveness_{index}_{label} → index */
    private static int livenessFrameIndex(VerificationDocument doc) {
        String rest = doc.getDocumentType().substring(VerificationService.LIVENESS_DOC_PREFIX.length());
        try {
            return Integer.parseInt(rest.substring(0, rest.indexOf('_')));
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** liveness_{index}_{label} → label */
    private static String livenessFrameLabel(VerificationDocument doc) {
        String rest = doc.getDocumentType().substring(VerificationService.LIVENESS_DOC_PREFIX.length());
        return rest.substring(rest.indexOf('_') + 1);
    }

    private boolean isManualReview(FaceVerificationResponse faceResponse) {
        return "manual_review".equalsIgnoreCase(
                Optional.ofNullable(faceResponse.getReviewStatus())
                        .map(status -> status.toLowerCase(Locale.ROOT))
                        .orElse(null));
    }

    @Data
    @Builder
    public static class VerificationResult {
        private String verificationId;
        private String status;
        private List<CheckResult> checks;
        private String overallDecision;
        private Double riskScore;
        private String failureReason;
        private String errorDetails;
        // OCR extracted fields — persisted to the id_front VerificationDocument
        private Map<String, Object> extractedOcrData;
        private String ocrDocumentId;
    }

    @Data
    @Builder
    public static class CheckResult {
        private String stepName;
        private String status;
        private Double confidenceScore;
        private Map<String, Object> result;
        private String errorMessage;
    }
}
