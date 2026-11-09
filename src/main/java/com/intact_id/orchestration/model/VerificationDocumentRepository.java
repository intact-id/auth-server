package com.intact_id.orchestration.model;

import com.intact_id.orchestration.VerificationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationDocumentRepository extends JpaRepository<VerificationDocument, String> {
    List<VerificationDocument> findByVerificationRequestId(String verificationRequestId);
}
