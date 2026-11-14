package com.intact_id.orchestration.privacy;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One erasure of a verification's data: what went, why and who did it. Kept after the data is gone. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "data_erasures")
public class DataErasure extends Auditable {

    public enum Scope { DOCUMENTS, PERSONAL_DATA }

    public enum Trigger { MANUAL, RETENTION }

    @Column(name = "verification_id", nullable = false)
    private String verificationId;

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private Scope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private Trigger trigger;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "documents_deleted", nullable = false)
    private int documentsDeleted;
}
