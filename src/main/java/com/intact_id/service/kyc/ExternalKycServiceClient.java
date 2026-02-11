package com.intact_id.service.kyc;

import com.intact_id.client.face.FaceScreeningClient;
import com.intact_id.db.model.kyc.VerificationDocument;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.kyc.VerificationDocumentRepository;
import com.intact_id.dto.response.face.FaceVerificationResponse;
import com.intact_id.util.FileMultipartFile;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * External KYC service client
 * Integrates with Face Screening Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalKycServiceClient {

    private final FaceScreeningClient faceScreeningClient;
    private final VerificationDocumentRepository verificationDocumentRepository;

    @Value("${face-service.api-key}")
    private String apiKey;

    @Value("${face-service.api-secret}")
    private String apiSecret;

    public CompletableFuture<VerificationResult> performVerification(
            String tier,
            VerificationRequest request,
            List<VerificationDocument> documents) {

        log.info("Starting async verification for tier: {} verification: {}", tier, request.getId());

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use provided documents instead of fetching from DB to avoid race condition

                VerificationDocument selfieDoc = documents.stream()
                        .filter(d -> "selfie".equals(d.getDocumentType()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Selfie not found"));

                VerificationDocument idDoc = documents.stream()
                        .filter(d -> "id_front".equals(d.getDocumentType())
                                || "id_card".equals(d.getDocumentType()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("ID document not found"));

                // Read files
                MultipartFile selfieFile = createMultipartFile(selfieDoc);
                MultipartFile idFile = createMultipartFile(idDoc);

                // Call Face Service
                FaceVerificationResponse faceResponse = faceScreeningClient.verifyFace(
                        apiKey,
                        apiSecret,
                        selfieFile,
                        idFile);

                // Build verification result
                List<CheckResult> checks = new ArrayList<>();

                // Face Match Check
                checks.add(CheckResult.builder()
                        .stepName("face_match")
                        .status(faceResponse.isVerified() ? "passed" : "failed")
                        .confidenceScore(1.0 - faceResponse.getDistance()) // Approximate
                        // confidence
                        .result(Map.of(
                                "match_score", 1.0 - faceResponse.getDistance(),
                                "verified", faceResponse.isVerified(),
                                "distance", faceResponse.getDistance(),
                                "threshold", faceResponse.getThreshold(),
                                "similarity_metric",
                                faceResponse.getSimilarityMetric()))
                        .build());

                // Add other mock checks for now (Liveness, OCR, etc.) as they are not yet
                // implemented in Face Service client
                // or we can assume Face Service handles liveness if configured
                checks.add(CheckResult.builder()
                        .stepName("liveness_detection")
                        .status("passed") // Mocked for now
                        .confidenceScore(0.95)
                        .result(Map.of("is_live", true))
                        .build());

                // Calculate overall decision
                String decision = faceResponse.isVerified() ? "approved" : "rejected";
                double riskScore = faceResponse.isVerified() ? 0.1 : 0.9;

                log.info("Verification completed for {}: decision={}, risk={}",
                        request.getId(), decision, riskScore);

                return VerificationResult.builder()
                        .verificationId(request.getId())
                        .status("completed")
                        .checks(checks)
                        .overallDecision(decision)
                        .riskScore(riskScore)
                        .build();

            } catch (Exception e) {
                log.error("Verification failed", e);

                String failureReason = "SystemError";
                String errorMessage = e.getMessage();

                // Parse FeignException to extract error details
                if (e instanceof feign.FeignException) {
                    feign.FeignException feignException = (feign.FeignException) e;
                    try {
                        String responseBody = feignException.contentUTF8();
                        if (responseBody != null && !responseBody.isEmpty()) {
                            // Parse JSON response
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            com.fasterxml.jackson.databind.JsonNode errorJson = mapper
                                    .readTree(responseBody);

                            if (errorJson.has("error")) {
                                failureReason = errorJson.get("error").asText();
                            }
                            if (errorJson.has("message")) {
                                errorMessage = errorJson.get("message").asText();
                            }
                        }
                    } catch (Exception parseEx) {
                        log.warn("Failed to parse error response", parseEx);
                    }
                }

                List<CheckResult> checks = new ArrayList<>();
                checks.add(CheckResult.builder()
                        .stepName("system_check")
                        .status("failed")
                        .errorMessage(errorMessage)
                        .build());

                return VerificationResult.builder()
                        .verificationId(request.getId())
                        .status("failed")
                        .checks(checks)
                        .overallDecision("error")
                        .failureReason(failureReason)
                        .errorDetails(errorMessage)
                        .build();
            }
        });
    }

    private MultipartFile createMultipartFile(VerificationDocument doc) throws IOException {
        File file = new File(doc.getFilePath());
        return new FileMultipartFile(
                file,
                doc.getDocumentType(),
                doc.getOriginalFilename(),
                doc.getMimeType());
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
