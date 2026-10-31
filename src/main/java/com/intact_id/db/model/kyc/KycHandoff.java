package com.intact_id.db.model.kyc;

import com.intact_id.constant.base.Auditable;
import com.intact_id.constant.enums.kyc.KycHandoffStatus;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "kyc_handoffs")
@EqualsAndHashCode(callSuper = false)
public class KycHandoff extends Auditable {

    @Column(name = "draft_id", nullable = false)
    private String draftId;

    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycHandoffStatus status = KycHandoffStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "claimed_user_agent")
    private String claimedUserAgent;

    public boolean isClaimable() {
        return status == KycHandoffStatus.PENDING && expiresAt.isAfter(LocalDateTime.now());
    }
}
