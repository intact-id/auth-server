package com.intact_id.service.kyc;

import com.intact_id.client.face.FaceScreeningClient;
import com.intact_id.client.ocr.OcrScreeningClient;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.dto.response.face.FaceVerificationResponse;
import com.intact_id.dto.response.face.LivenessResponse;
import com.intact_id.dto.response.ocr.OcrExtractionResponse;
import com.intact_id.service.kyc.OcrValidationService.ValidationDecision;
import com.intact_id.service.kyc.OcrValidationService.ValidationOutcome;
import com.intact_id.util.FileMultipartFile;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalKycServiceClient {

    private final FaceScreeningClient faceScreeningClient;
    private final OcrScreeningClient ocrScreeningClient;
    private final OcrValidationService ocrValidationService;
    private final VerificationDocumentRepository verificationDocumentRepository;

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

        log.info("Starting verification for tier={} id={}", tier, request.getId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                VerificationDocument selfieDoc = documents.stream()
                        .filter(d -> "selfie".equals(d.getDocumentType()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Selfie not found"));

                VerificationDocument idDoc = documents.stream()
                        .filter(d -> "id_front".equals(d.getDocumentType())
                                || "id_card".equals(d.getDocumentType()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("ID document not found"));

                // Each service call gets its own MultipartFile instance to avoid shared stream state
                MultipartFile selfieForFace = createMultipartFile(selfieDoc);
                MultipartFile selfieForLiveness = createMultipartFile(selfieDoc);
                MultipartFile idFileForFace = createMultipartFile(idDoc);
                MultipartFile idFileForOcr = createMultipartFile(idDoc);

                // Face match — critical, failure propagates
                CompletableFuture<FaceVerificationResponse> faceFuture = CompletableFuture
                        .supplyAsync(() -> faceScreeningClient.verifyFace(
                                faceApiKey, faceApiSecret, selfieForFace, idFileForFace));

                // Liveness — degrades to manual_review if service is down
                CompletableFuture<LivenessResponse> livenessFuture = CompletableFuture
                        .supplyAsync(() -> faceScreeningClient.checkLiveness(
                                faceApiKey, faceApiSecret, selfieForLiveness))
                        .exceptionally(ex -> {
                            log.warn("Liveness check unavailable for {}: {}", request.getId(), ex.getMessage());
                            return null;
                        });

                // OCR extraction — degrades to manual_review if service is down
                CompletableFuture<OcrExtractionResponse> ocrFuture = CompletableFuture
                        .supplyAsync(() -> ocrScreeningClient.extractDocument(
                                ocrApiKey, ocrApiSecret, idFileForOcr))
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
                checks.add(CheckResult.builder()
                        .stepName("face_match")
                        .status(faceResponse.isVerified() ? "passed" : "failed")
                        .confidenceScore(1.0 - faceResponse.getDistance())
                        .result(Map.of(
                                "match_score", 1.0 - faceResponse.getDistance(),
                                "verified", faceResponse.isVerified(),
                                "distance", faceResponse.getDistance(),
                                "threshold", faceResponse.getThreshold(),
                                "similarity_metric", faceResponse.getSimilarityMetric()))
                        .build());

                if (!faceResponse.isVerified()) {
                    overallDecision = "rejected";
                    riskScore = 0.9;
                }

                // --- Liveness (null = service was down → manual review) ---
                LivenessResponse livenessResponse = livenessFuture.join();
                if (livenessResponse != null) {
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
                        .ocrDocumentId(idDoc.getId())
                        .build();

            } catch (Exception e) {
                log.error("Verification failed for {}", request.getId(), e);
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

    private MultipartFile createMultipartFile(VerificationDocument doc) throws IOException {
        File file = new File(doc.getFilePath());
        return new FileMultipartFile(file, doc.getDocumentType(),
                doc.getOriginalFilename(), doc.getMimeType());
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
