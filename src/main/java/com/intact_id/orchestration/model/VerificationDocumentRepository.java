package com.intact_id.orchestration.model;

import com.intact_id.orchestration.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, String> {
    List<VerificationDocument> findByVerificationRequestId(String verificationRequestId);

    /** Document counts for a page of verifications in one query: each row is [verificationId, count]. */
    @Query("SELECT d.verificationRequest.id, COUNT(d) FROM VerificationDocument d " +
            "WHERE d.verificationRequest.id IN :verificationIds GROUP BY d.verificationRequest.id")
    List<Object[]> countByVerificationIds(@Param("verificationIds") Collection<String> verificationIds);
}
