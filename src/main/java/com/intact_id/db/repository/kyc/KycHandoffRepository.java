package com.intact_id.db.repository.kyc;

import com.intact_id.constant.enums.kyc.KycHandoffStatus;
import com.intact_id.db.model.kyc.KycHandoff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface KycHandoffRepository extends JpaRepository<KycHandoff, String> {

        Optional<KycHandoff> findByCodeHash(String codeHash);

        Optional<KycHandoff> findFirstByDraftIdOrderByCreatedAtDesc(String draftId);

        // Single-use: only one caller can move a code from PENDING to CLAIMED
        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KycHandoff h SET h.status = com.intact_id.constant.enums.kyc.KycHandoffStatus.CLAIMED, "
                        + "h.claimedAt = :now, h.claimedUserAgent = :userAgent "
                        + "WHERE h.id = :id AND h.status = com.intact_id.constant.enums.kyc.KycHandoffStatus.PENDING "
                        + "AND h.expiresAt > :now")
        int claim(@Param("id") String id,
                        @Param("now") LocalDateTime now,
                        @Param("userAgent") String userAgent);

        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KycHandoff h SET h.status = com.intact_id.constant.enums.kyc.KycHandoffStatus.REVOKED "
                        + "WHERE h.draftId = :draftId AND h.status IN :statuses")
        int revokeAllForDraft(@Param("draftId") String draftId,
                        @Param("statuses") Collection<KycHandoffStatus> statuses);
}
