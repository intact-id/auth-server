package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entity to track webhook delivery attempts
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_webhook_company", columnList = "company_id"),
        @Index(name = "idx_webhook_status", columnList = "status"),
        @Index(name = "idx_webhook_next_retry", columnList = "next_retry_at")
})
public class WebhookDelivery extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "verification_id")
    private String verificationId;

    @Column(name = "webhook_url", nullable = false, length = 500)
    private String webhookUrl;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum DeliveryStatus {
        PENDING,
        DELIVERED,
        FAILED,
        RETRYING
    }

    public void markAsDelivered(int statusCode, String responseBody) {
        this.status = DeliveryStatus.DELIVERED;
        this.httpStatusCode = statusCode;
        this.responseBody = responseBody;
        this.deliveredAt = LocalDateTime.now();
    }

    public void markAsFailed(int statusCode, String errorMessage) {
        this.status = DeliveryStatus.FAILED;
        this.httpStatusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public void incrementRetry() {
        this.retryCount++;
        this.status = DeliveryStatus.RETRYING;
    }
}
