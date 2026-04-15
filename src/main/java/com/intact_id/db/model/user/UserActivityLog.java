package com.intact_id.db.model.user;

import com.intact_id.constant.base.Auditable;
import com.intact_id.db.model.Company;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "user_activity_logs", indexes = {
        @Index(name = "idx_user_activity_logs_session_id", columnList = "session_id_fk"),
        @Index(name = "idx_user_activity_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_user_activity_logs_company_id", columnList = "company_id"),
        @Index(name = "idx_user_activity_logs_started_at", columnList = "started_at"),
        @Index(name = "idx_user_activity_logs_http_method", columnList = "http_method"),
        @Index(name = "idx_user_activity_logs_path", columnList = "path")
})
@EqualsAndHashCode(callSuper = false)
public class UserActivityLog extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id_fk", nullable = false)
    private UserSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "query_string", columnDefinition = "TEXT")
    private String queryString;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
