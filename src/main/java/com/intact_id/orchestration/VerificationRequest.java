package com.intact_id.orchestration;

import com.intact_id.workflow.WorkflowRun;
import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.Company;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    // The workflow version this run follows; null = tier-based run
    @Column(name = "workflow_id")
    private String workflowId;

    @Column(name = "workflow_version")
    private Integer workflowVersion;

    // Every node taken and the output it left by, for the audit trail
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_path", columnDefinition = "jsonb")
    private List<WorkflowRun.Step> workflowPath;

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

    @Column(name = "external_reference")
    private String externalReference; // company-supplied ID, not unique

    // Manual review record. Kept apart from riskScore and failureReason so the automated
    // evidence survives a reviewer's decision.
    @Column(name = "review_reason", length = 500)
    private String reviewReason; // why the checks sent it to a human

    @Column(name = "review_requested_at")
    private LocalDateTime reviewRequestedAt;

    @Column(name = "review_assigned_to")
    private String reviewAssignedTo; // reviewer's username

    @Column(name = "review_assigned_at")
    private LocalDateTime reviewAssignedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy; // reviewer's username

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    // Helper methods
    public boolean isCompleted() {
        return status == GlobalStatuses.COMPLETED ||
                status == GlobalStatuses.APPROVED ||
                status == GlobalStatuses.REJECTED;
    }

    public void markAsProcessing() {
        this.status = GlobalStatuses.PROCESSING;
    }

    public void markAsHeld(String reason) {
        this.status = GlobalStatuses.ON_HOLD;
        if (this.metadata == null) this.metadata = new HashMap<>();
        this.metadata.put("holdReason", reason);
        this.metadata.put("heldAt", java.time.LocalDateTime.now().toString());
    }

    public void markAsSubmitted() {
        this.status = GlobalStatuses.SUBMITTED;
        if (this.metadata != null) {
            this.metadata.remove("holdReason");
            this.metadata.remove("heldAt");
        }
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

    public void markAsManualReview(Double riskScore, String reason) {
        this.status = GlobalStatuses.PENDING_APPROVAL;
        this.overallDecision = "manual_review";
        this.riskScore = riskScore;
        this.reviewReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.reviewRequestedAt = LocalDateTime.now();
    }

    /** Records the reviewer's decision. The automated riskScore and failureReason are left as evidence. */
    public void resolveManualReview(String decision, String notes, String reviewer) {
        if ("approved".equalsIgnoreCase(decision)) {
            this.status = GlobalStatuses.COMPLETED;
            this.overallDecision = "approved";
        } else {
            this.status = GlobalStatuses.REJECTED;
            this.overallDecision = "rejected";
        }
        this.reviewedBy = reviewer;
        this.reviewedAt = LocalDateTime.now();
        this.reviewNotes = notes;
        this.completedAt = this.reviewedAt;
    }

    public void assignReviewer(String reviewer) {
        this.reviewAssignedTo = reviewer;
        this.reviewAssignedAt = LocalDateTime.now();
    }

    public void unassignReviewer() {
        this.reviewAssignedTo = null;
        this.reviewAssignedAt = null;
    }
}
