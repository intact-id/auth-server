package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class VerificationStatusResponseDto {
    private String verificationId;
    private String companyName;
    private String status;
    private String tier;
    private String verificationType;
    private String overallDecision;
    private String failureReason;
    private Double riskScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Map<String, Object> customerData;
    private List<VerificationStepDto> steps;
    private List<DocumentDto> documents;

    @Data
    @Builder
    public static class VerificationStepDto {
        private String stepName;
        private String status;
        private Double confidenceScore;
        private Map<String, Object> result;
        private LocalDateTime executedAt;
        private String errorMessage;
    }

    @Data
    @Builder
    public static class DocumentDto {
        private String id;
        private String documentType;
        private String originalFilename;
        private Long fileSize;
        private String mimeType;
        private LocalDateTime uploadedAt;
    }
}
