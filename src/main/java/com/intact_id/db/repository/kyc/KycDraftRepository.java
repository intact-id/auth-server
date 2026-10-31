package com.intact_id.db.repository.kyc;

import com.intact_id.constant.enums.kyc.KycDraftStatus;
import com.intact_id.db.model.kyc.KycDraft;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KycDraftRepository extends JpaRepository<KycDraft, String> {

        Optional<KycDraft> findByResumeTokenHash(String resumeTokenHash);

        // Compare-and-set so two devices can't both submit the same draft
        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KycDraft d SET d.status = :to, d.version = d.version + 1 WHERE d.id = :id AND d.status = :from")
        int transitionStatus(@Param("id") String id,
                        @Param("from") KycDraftStatus from,
                        @Param("to") KycDraftStatus to);

        @Query("SELECT d.id FROM KycDraft d WHERE d.expiresAt < :now ORDER BY d.expiresAt ASC")
        List<String> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);
}
