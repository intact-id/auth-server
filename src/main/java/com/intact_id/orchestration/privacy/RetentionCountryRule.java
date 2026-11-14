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

/** What Intact allows for one country ('*' = any other) and kind of data. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "retention_country_rules")
public class RetentionCountryRule extends Auditable {

    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RetentionCategory category;

    @Column(name = "min_days")
    private Integer minDays;

    @Column(name = "max_days")
    private Integer maxDays;

    @Column(name = "default_days")
    private Integer defaultDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "starts_from", nullable = false, length = 20)
    private StartsFrom startsFrom = StartsFrom.FINISHED;

    @Column(name = "legal_basis", length = 500)
    private String legalBasis;
}
