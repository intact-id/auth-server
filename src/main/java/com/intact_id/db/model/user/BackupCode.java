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
@Table(name = "backup_codes", indexes = {
        @Index(name = "idx_backup_codes_user_id", columnList = "user_id"),
        @Index(name = "idx_backup_codes_code_hash", columnList = "code_hash")
})
@EqualsAndHashCode(callSuper = false)
public class BackupCode extends Auditable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", unique = true, nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public boolean isUsed() {
        return usedAt != null;
    }
}
