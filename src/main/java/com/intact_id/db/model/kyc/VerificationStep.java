package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
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

@Entity
@Getter
@Setter
@Table(name = "verification_steps")
@EqualsAndHashCode(callSuper = false)
public class VerificationStep extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_id", nullable = false)
    private VerificationRequest verificationRequest;

    @Column(name = "step_name", nullable = false, length = 50)
    private String stepName; // ocr, face_match, liveness, iprs, sanctions, pep

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatuses status = GlobalStatuses.PENDING;

    @Column(name = "result", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> result = new HashMap<>();

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "error_message")
    private String errorMessage;

    // Helper methods
    public void markAsPassed(Double confidence, Map<String, Object> resultData) {
        this.status = GlobalStatuses.COMPLETED;
        this.confidenceScore = confidence;
        this.result = resultData;
        this.executedAt = LocalDateTime.now();
    }

    public void markAsFailed(String error) {
        this.status = GlobalStatuses.FAILED;
        this.errorMessage = error;
        this.executedAt = LocalDateTime.now();
    }
}
