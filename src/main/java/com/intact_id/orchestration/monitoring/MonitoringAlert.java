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
import java.util.List;
import java.util.Map;

/** A change re-screening found, for the company to confirm (it is them) or dismiss (it is not). */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "monitoring_alerts")
public class MonitoringAlert extends Auditable {

    public enum Kind { NEW_MATCH, MATCH_CHANGED, MATCH_REMOVED }

    public enum Status { OPEN, CONFIRMED, DISMISSED, INFO }

    @Column(name = "company_id", nullable = false)
    private String companyId;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private Kind kind;

    @Column(name = "previous_verdict", length = 20)
    private String previousVerdict;

    @Column(name = "verdict", length = 20)
    private String verdict;

    // [{entityId, caption, score, datasets, topics, newTopics}]
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matches", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> matches;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;
}
