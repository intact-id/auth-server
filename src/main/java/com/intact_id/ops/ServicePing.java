package com.intact_id.ops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One health check of one service. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "service_pings", indexes = @Index(name = "idx_service_pings_service_checked", columnList = "service, checked_at"))
class ServicePing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service", nullable = false, length = 40)
    private String service;

    @Column(name = "up", nullable = false)
    private boolean up;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    /** HTTP status, or null when the service could not be reached (or for the database). */
    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "error", length = 300)
    private String error;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    ServicePing(String service, boolean up, long latencyMs, Integer statusCode, String error, LocalDateTime checkedAt) {
        this.service = service;
        this.up = up;
        this.latencyMs = latencyMs;
        this.statusCode = statusCode;
        this.error = error == null || error.length() <= 300 ? error : error.substring(0, 300);
        this.checkedAt = checkedAt;
    }
}
