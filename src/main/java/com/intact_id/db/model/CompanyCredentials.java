package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 17:41
 */

@Entity
@EqualsAndHashCode(callSuper = false)
public class CompanyCredentials extends Auditable {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;


    @Column(name = "api_key_hash", unique = true)
    private String apiKeyHash;

    @Column(name = "api_secret_hash")
    private String apiSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatuses status = GlobalStatuses.ACTIVE;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour ;

    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay;

    @Column(name = "rate_limit_per_month")
    private Integer rateLimitPerMonth;

    @Column(name = "total_requests")
    private Long totalRequests = 0L;

    @Column(name = "total_successful_requests")
    private Long totalSuccessfulRequests = 0L;

    @Column(name = "total_failed_requests")
    private Long totalFailedRequests = 0L;

    @Column(name = "security_settings", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    private Map<String, Object> securitySettings = new HashMap<>();
    // {
    //   "ip_whitelist": ["192.168.1.1", "10.0.0.0/24"],
    //   "allowed_origins": ["https://client.com"],
    //   "require_signature": false,
    //   "enforce_https": true
    // }


    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    private Map<String, Object> metadata = new HashMap<>();

    //helper methods

    public boolean isActive() {
        return this.status == GlobalStatuses.ACTIVE &&
                (this.expiresAt == null || this.expiresAt.isAfter(LocalDateTime.now()));
    }

    public boolean hasExpired() {
        return this.expiresAt != null && this.expiresAt.isBefore(LocalDateTime.now());
    }

    public void suspend() {
        this.status = GlobalStatuses.SUSPENDED;
    }

    public void revoke() {
        this.status = GlobalStatuses.REVOKED;
    }

    public void activate() {
        this.status = GlobalStatuses.ACTIVE;
    }

    public void recordUsage(boolean success) {
        this.totalRequests++;
        if (success) {
            this.totalSuccessfulRequests++;
        } else {
            this.totalFailedRequests++;
        }
        this.lastUsedAt = LocalDateTime.now();
    }

    public double getSuccessRate() {
        if (totalRequests == 0) return 0.0;
        return (totalSuccessfulRequests * 100.0) / totalRequests;
    }

    public boolean isWithinRateLimit(int requestsInLastHour) {
        return requestsInLastHour < rateLimitPerHour;
    }}
