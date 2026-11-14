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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A person or business being screened again on a schedule, with the last result to compare against. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "monitored_subjects")
public class MonitoredSubject extends Auditable {

    public enum Kind { PERSON, BUSINESS }

    public enum Status { ACTIVE, STOPPED }

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "verification_id", nullable = false)
    private String verificationId;

    @Column(name = "party_id")
    private String partyId;

    @Column(name = "external_reference")
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private Kind kind;

    @Column(name = "display_name", nullable = false, length = 500)
    private String displayName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "date_of_birth", length = 20)
    private String dateOfBirth;

    @Column(name = "business_name", length = 500)
    private String businessName;

    @Column(name = "jurisdiction", length = 10)
    private String jurisdiction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "stopped_reason", length = 500)
    private String stoppedReason;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    // null until the first screen (the baseline)
    @Column(name = "verdict", length = 20)
    private String verdict;

    // [{entityId, caption, score, datasets, topics}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matches", columnDefinition = "jsonb")
    private List<Map<String, Object>> matches = new ArrayList<>();

    // entityId -> topics it had when dismissed; alerts again only if it gains topics
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dismissed", columnDefinition = "jsonb")
    private Map<String, List<String>> dismissed = new HashMap<>();

    @Column(name = "last_screened_at")
    private LocalDateTime lastScreenedAt;

    @Column(name = "next_screen_at")
    private LocalDateTime nextScreenAt;

    @Column(name = "failures", nullable = false)
    private int failures;

    @Column(name = "open_alerts", nullable = false)
    private int openAlerts;
}
