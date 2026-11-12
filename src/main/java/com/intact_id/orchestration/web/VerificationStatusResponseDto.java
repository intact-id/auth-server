package com.intact_id.orchestration.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.intact_id.notifications.WebhookDeliveryView;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class VerificationStatusResponseDto {
    private String verificationId;
    private String externalReference;
    private String companyName;
    private String status;
    private String tier;
    private String verificationType;
    private String environment;
    private String workflowId;
    private Integer workflowVersion;
    private String overallDecision;
    private String failureReason;
    private String errorDetails;
    private Double riskScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Map<String, Object> customerData;
    private Integer documentCount;
    private List<VerificationStepDto> steps;
    private List<DocumentDto> documents;

    // Staff only (ADMIN / SUPER_ADMIN). Left out of the JSON for merchants and the widget.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ReviewDto review;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<WebhookDeliveryView> webhookDeliveries;

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
        private Map<String, Object> extractedData;
    }

    /** The manual review record. Empty fields mean that stage has not happened. */
    @Data
    @Builder
    public static class ReviewDto {
        private String reason;
        private LocalDateTime requestedAt;
        private String assignedTo;
        private LocalDateTime assignedAt;
        private String reviewedBy;
        private LocalDateTime reviewedAt;
        private String notes;
    }
}
