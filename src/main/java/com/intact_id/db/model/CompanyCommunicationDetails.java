package com.intact_id.db.model;

import com.intact_id.constant.base.Auditable;
import com.intact_id.util.converter.PgJsonbToMapConverter;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 16/11/2025 17:14
 */


@Entity
@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@Table(name = "company_communications", indexes = {
        @Index(name = "idx_comm_email", columnList = "email"),
        @Index(name = "idx_comm_phone", columnList = "phone")
})
public class CompanyCommunicationDetails extends Auditable { ;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;


    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "website")
    private String website;

    @Column(name = "secondary_email")
    private String secondaryEmail;

    @Column(name = "secondary_phone")
    private String secondaryPhone;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "billing_email")
    private String billingEmail;


    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "webhook_enabled")
    private Boolean webhookEnabled = false;

    @Column(name = "email_notifications")
    private Boolean emailNotifications = true;

    @Column(name = "sms_notifications")
    private Boolean smsNotifications = false;

    /**
     *   "notify_on_verification_complete": true,
     *   "notify_on_verification_failed": true,
     *   "notify_on_low_balance": true,
     *   "daily_summary": false,
     *   "webhook_retry_count": 3,
     *   "webhook_timeout_seconds": 30
     */

    @Column(name = "notification_settings", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Convert(converter = PgJsonbToMapConverter.class)
    private Map<String, Object> notificationSettings = new HashMap<>();



    public boolean shouldNotifyByEmail() {
        return emailNotifications != null && emailNotifications;
    }

    public boolean shouldNotifyBySms() {
        return smsNotifications != null && smsNotifications;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled != null && webhookEnabled && webhookUrl != null;
    }

    public int getWebhookRetryCount() {
        return (Integer) notificationSettings.getOrDefault("webhook_retry_count", 3);
    }

    public int getWebhookTimeoutSeconds() {
        return (Integer) notificationSettings.getOrDefault("webhook_timeout_seconds", 30);
    }
}