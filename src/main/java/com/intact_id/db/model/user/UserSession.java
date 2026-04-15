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
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_user_sessions_user_id", columnList = "user_id"),
        @Index(name = "idx_user_sessions_company_id", columnList = "company_id"),
        @Index(name = "idx_user_sessions_login_at", columnList = "login_at"),
        @Index(name = "idx_user_sessions_status", columnList = "status")
})
@EqualsAndHashCode(callSuper = false)
public class UserSession extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refresh_token_id")
    private RefreshToken refreshToken;

    @Column(name = "session_id", nullable = false, unique = true, length = 255)
    private String sessionId;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
