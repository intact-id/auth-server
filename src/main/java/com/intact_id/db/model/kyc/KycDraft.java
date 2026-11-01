package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.kyc.KycDraftStatus;
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
@Table(name = "kyc_drafts")
@EqualsAndHashCode(callSuper = false)
public class KycDraft extends Auditable {

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 10)
    private ApiKeyEnvironment environment;

    @Column(name = "tier", nullable = false, length = 20)
    private String tier;

    @Column(name = "external_reference")
    private String externalReference;

    @Column(name = "details", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycDraftStatus status = KycDraftStatus.IN_PROGRESS;

    @Column(name = "verification_id")
    private String verificationId;

    @Column(name = "resume_token_hash", nullable = false, length = 64)
    private String resumeTokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "status_changed_at")
    private LocalDateTime statusChangedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
