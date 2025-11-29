package com.intact_id.db.repository.kyc;

import com.intact_id.db.model.kyc.VerificationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationStepRepository extends JpaRepository<VerificationStep, String> {
    List<VerificationStep> findByVerificationRequestIdOrderByCreatedAtAsc(String verificationRequestId);
}
