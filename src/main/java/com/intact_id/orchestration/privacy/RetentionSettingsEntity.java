package com.intact_id.orchestration.privacy;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How long applicant data is kept. company_id null is the platform default; a company row overrides it. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "retention_settings")
public class RetentionSettingsEntity extends Auditable {

    @Column(name = "company_id", unique = true)
    private String companyId;

    // Days after a verification finishes; null = keep
    @Column(name = "documents_days")
    private Integer documentsDays;

    @Column(name = "personal_data_days")
    private Integer personalDataDays;
}
