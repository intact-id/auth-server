package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "verification_requests")
@EqualsAndHashCode(callSuper = false)
public class VerificationRequest extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier; // tier1, tier2, tier3

    @Column(name = "verification_type", length = 20)
    private String verificationType; // individual, business

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatuses status = GlobalStatuses.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private ApiKeyEnvironment environment = ApiKeyEnvironment.PROD;

    @Column(name = "customer_data", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> customerData = new HashMap<>();

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "overall_decision", length = 50)
    private String overallDecision; // approved, rejected, manual_review

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "failure_reason", length = 100)
    private String failureReason; // e.g., "FaceNotDetectedError"

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails; // Detailed error message

    @Column(name = "billing_amount", precision = 19, scale = 4)
    private BigDecimal billingAmount;

    @Column(name = "billed_at")
    private LocalDateTime billedAt;

    // Helper methods
    public boolean isCompleted() {
        return status == GlobalStatuses.COMPLETED ||
                status == GlobalStatuses.APPROVED ||
                status == GlobalStatuses.REJECTED;
    }

    public void markAsProcessing() {
        this.status = GlobalStatuses.PROCESSING;
    }

    public void markAsCompleted(String decision, Double riskScore) {
        this.status = GlobalStatuses.COMPLETED;
        this.overallDecision = decision;
        this.riskScore = riskScore;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed() {
        this.status = GlobalStatuses.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsFailed(String failureReason, String errorDetails) {
        this.status = GlobalStatuses.FAILED;
        this.failureReason = failureReason;
        this.errorDetails = errorDetails;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsRejected(String failureReason, String errorDetails, Double riskScore) {
        this.status = GlobalStatuses.REJECTED;
        this.overallDecision = "rejected";
        this.failureReason = failureReason;
        this.errorDetails = errorDetails;
        this.riskScore = riskScore;
        this.completedAt = LocalDateTime.now();
    }

    public void markAsManualReview(Double riskScore) {
        this.status = GlobalStatuses.PENDING_APPROVAL;
        this.overallDecision = "manual_review";
        this.riskScore = riskScore;
    }

    public void resolveManualReview(String decision, String reviewerNotes) {
        if ("approved".equalsIgnoreCase(decision)) {
            this.status = GlobalStatuses.COMPLETED;
            this.overallDecision = "approved";
            this.riskScore = 0.1;
        } else {
            this.status = GlobalStatuses.REJECTED;
            this.overallDecision = "rejected";
            this.riskScore = 0.9;
        }
        this.failureReason = reviewerNotes;
        this.completedAt = LocalDateTime.now();
    }
}
