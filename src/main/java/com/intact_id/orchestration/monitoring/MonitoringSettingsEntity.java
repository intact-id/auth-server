package com.intact_id.orchestration.monitoring;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A company's monitoring settings; a company without a row has monitoring off. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "monitoring_settings")
public class MonitoringSettingsEntity extends Auditable {

    @Column(name = "company_id", nullable = false, unique = true)
    private String companyId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 10)
    private Frequency frequency = Frequency.DAILY;

    @Column(name = "include_people", nullable = false)
    private boolean includePeople = true;

    @Column(name = "include_businesses", nullable = false)
    private boolean includeBusinesses = true;

    @Column(name = "include_parties", nullable = false)
    private boolean includeParties = true;

    public enum Frequency {
        DAILY(1), WEEKLY(7), MONTHLY(30);

        public final int days;

        Frequency(int days) {
            this.days = days;
        }
    }
}
