package com.intact_id.db.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 17:41
 */

@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = false, exclude = "company")
public class CompanyCredentials extends Auditable {
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;


    @Column(name = "api_key_hash", unique = true)
    private String apiKeyHash;

    @Column(name = "api_secret_hash")
    private String apiSecretHash;

    @Column(name = "dev_api_key_hash", unique = true)
    private String devApiKeyHash;

    @Column(name = "dev_api_secret_hash")
    private String devApiSecretHash;

    @Column(name = "prod_api_key_hash", unique = true)
    private String prodApiKeyHash;

    @Column(name = "prod_api_secret_hash")
    private String prodApiSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatuses status = GlobalStatuses.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "dev_status")
    private GlobalStatuses devStatus = GlobalStatuses.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "prod_status")
    private GlobalStatuses prodStatus = GlobalStatuses.ACTIVE;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "dev_last_used_at")
    private LocalDateTime devLastUsedAt;

    @Column(name = "prod_last_used_at")
    private LocalDateTime prodLastUsedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "dev_expires_at")
    private LocalDateTime devExpiresAt;

    @Column(name = "prod_expires_at")
    private LocalDateTime prodExpiresAt;

    @Column(name = "rate_limit_per_hour")
    private Integer rateLimitPerHour;

    @Column(name = "rate_limit_per_day")
    private Integer rateLimitPerDay;

    @Column(name = "rate_limit_per_month")
    private Integer rateLimitPerMonth;

    @Column(name = "dev_rate_limit_per_hour")
    private Integer devRateLimitPerHour;

    @Column(name = "dev_rate_limit_per_day")
    private Integer devRateLimitPerDay;

    @Column(name = "dev_rate_limit_per_month")
    private Integer devRateLimitPerMonth;

    @Column(name = "prod_rate_limit_per_hour")
    private Integer prodRateLimitPerHour;

    @Column(name = "prod_rate_limit_per_day")
    private Integer prodRateLimitPerDay;

    @Column(name = "prod_rate_limit_per_month")
    private Integer prodRateLimitPerMonth;

    @Column(name = "total_requests")
    private Long totalRequests = 0L;

    @Column(name = "total_successful_requests")
    private Long totalSuccessfulRequests = 0L;

    @Column(name = "total_failed_requests")
    private Long totalFailedRequests = 0L;

    @Column(name = "dev_total_requests")
    private Long devTotalRequests = 0L;

    @Column(name = "dev_total_successful_requests")
    private Long devTotalSuccessfulRequests = 0L;

    @Column(name = "dev_total_failed_requests")
    private Long devTotalFailedRequests = 0L;

    @Column(name = "prod_total_requests")
    private Long prodTotalRequests = 0L;

    @Column(name = "prod_total_successful_requests")
    private Long prodTotalSuccessfulRequests = 0L;

    @Column(name = "prod_total_failed_requests")
    private Long prodTotalFailedRequests = 0L;

    @Column(name = "security_settings", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> securitySettings = new HashMap<>();
    // {
    //   "ip_whitelist": ["192.168.1.1", "10.0.0.0/24"],
    //   "allowed_origins": ["https://client.com"],
    //   "require_signature": false,
    //   "enforce_https": true
    // }


    @Column(name = "metadata", columnDefinition = "jsonb")
    @Convert(converter = PgJsonbToMapConverter.class)
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata = new HashMap<>();

    //helper methods

    public boolean isActive() {
        return this.status == GlobalStatuses.ACTIVE &&
                (this.expiresAt == null || this.expiresAt.isAfter(LocalDateTime.now()));
    }

    public boolean isActive(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            GlobalStatuses effectiveStatus = devStatus != null ? devStatus : status;
            LocalDateTime effectiveExpiresAt = devExpiresAt != null ? devExpiresAt : expiresAt;
            return effectiveStatus == GlobalStatuses.ACTIVE &&
                    (effectiveExpiresAt == null || effectiveExpiresAt.isAfter(LocalDateTime.now()));
        }
        GlobalStatuses effectiveStatus = prodStatus != null ? prodStatus : status;
        LocalDateTime effectiveExpiresAt = prodExpiresAt != null ? prodExpiresAt : expiresAt;
        return effectiveStatus == GlobalStatuses.ACTIVE &&
                (effectiveExpiresAt == null || effectiveExpiresAt.isAfter(LocalDateTime.now()));
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

    public void recordUsage(ApiKeyEnvironment environment, boolean success) {
        recordUsage(success);
        if (environment == ApiKeyEnvironment.DEV) {
            this.devTotalRequests++;
            if (success) {
                this.devTotalSuccessfulRequests++;
            } else {
                this.devTotalFailedRequests++;
            }
            this.devLastUsedAt = LocalDateTime.now();
        } else {
            this.prodTotalRequests++;
            if (success) {
                this.prodTotalSuccessfulRequests++;
            } else {
                this.prodTotalFailedRequests++;
            }
            this.prodLastUsedAt = LocalDateTime.now();
        }
    }

    public String getApiKeyHash(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devApiKeyHash;
        }
        return prodApiKeyHash != null ? prodApiKeyHash : apiKeyHash;
    }

    public String getApiSecretHash(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devApiSecretHash;
        }
        return prodApiSecretHash != null ? prodApiSecretHash : apiSecretHash;
    }

    public LocalDateTime getExpiresAt(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devExpiresAt != null ? devExpiresAt : expiresAt;
        }
        return prodExpiresAt != null ? prodExpiresAt : expiresAt;
    }

    public double getSuccessRate() {
        if (totalRequests == 0) return 0.0;
        return (totalSuccessfulRequests * 100.0) / totalRequests;
    }

    public double getSuccessRate(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            if (devTotalRequests == 0) return 0.0;
            return (devTotalSuccessfulRequests * 100.0) / devTotalRequests;
        }
        if (prodTotalRequests == 0) return 0.0;
        return (prodTotalSuccessfulRequests * 100.0) / prodTotalRequests;
    }

    public boolean isWithinRateLimit(int requestsInLastHour) {
        return requestsInLastHour < rateLimitPerHour;
    }

    public Integer getRateLimitPerHour(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devRateLimitPerHour != null ? devRateLimitPerHour : rateLimitPerHour;
        }
        return prodRateLimitPerHour != null ? prodRateLimitPerHour : rateLimitPerHour;
    }

    public Integer getRateLimitPerDay(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devRateLimitPerDay != null ? devRateLimitPerDay : rateLimitPerDay;
        }
        return prodRateLimitPerDay != null ? prodRateLimitPerDay : rateLimitPerDay;
    }

    public Integer getRateLimitPerMonth(ApiKeyEnvironment environment) {
        if (environment == ApiKeyEnvironment.DEV) {
            return devRateLimitPerMonth != null ? devRateLimitPerMonth : rateLimitPerMonth;
        }
        return prodRateLimitPerMonth != null ? prodRateLimitPerMonth : rateLimitPerMonth;
    }
}
