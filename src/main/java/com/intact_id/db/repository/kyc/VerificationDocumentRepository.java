package com.intact_id.db.repository.kyc;

import com.intact_id.db.model.kyc.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, String> {
    List<VerificationDocument> findByVerificationRequestId(String verificationRequestId);
}
