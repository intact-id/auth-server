package com.intact_id.db.model.user;

import com.intact_id.constant.base.Auditable;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "trusted_devices", indexes = {
        @Index(name = "idx_trusted_devices_token_hash", columnList = "device_token_hash"),
        @Index(name = "idx_trusted_devices_user_id", columnList = "user_id"),
        @Index(name = "idx_trusted_devices_expires_at", columnList = "expires_at")
})
@EqualsAndHashCode(callSuper = false)
public class TrustedDevice extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_token_hash", unique = true, nullable = false)
    private String deviceTokenHash;

    @Column(name = "device_label")
    private String deviceLabel;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked")
    private Boolean revoked = false;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(revoked) && !isExpired();
    }

    public void revoke() {
        this.revoked = true;
    }
}
