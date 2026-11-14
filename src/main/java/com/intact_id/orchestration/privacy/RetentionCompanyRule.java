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

/** A company's own period for one country ('*' = every country) and kind of data. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "retention_company_rules")
public class RetentionCompanyRule extends Auditable {

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RetentionCategory category;

    @Column(name = "days", nullable = false)
    private int days;
}
