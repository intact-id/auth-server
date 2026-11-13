package com.intact_id.orchestration.kyb;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Who must be checked behind a business. company_id null is the platform default; a company row overrides it. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "kyb_settings")
public class KybSettingsEntity extends Auditable {

    @Column(name = "company_id", unique = true)
    private String companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_check", nullable = false, length = 20)
    private OwnerCheck ownerCheck;

    @Column(name = "ownership_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal ownershipThreshold;

    @Column(name = "include_directors", nullable = false)
    private boolean includeDirectors;

    // Workflow key each person's verification runs (FULL_KYC); null = the company's default, else tier1
    @Column(name = "person_workflow", length = 100)
    private String personWorkflow;
}
