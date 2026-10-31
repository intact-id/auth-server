package com.intact_id.db.repository.kyc;

import com.intact_id.db.model.kyc.KycDraftDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface KycDraftDocumentRepository extends JpaRepository<KycDraftDocument, String> {

        List<KycDraftDocument> findByDraftId(String draftId);

        Optional<KycDraftDocument> findByDraftIdAndDocumentType(String draftId, String documentType);

        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("DELETE FROM KycDraftDocument d WHERE d.draftId = :draftId")
        int deleteAllByDraftId(@Param("draftId") String draftId);
}
