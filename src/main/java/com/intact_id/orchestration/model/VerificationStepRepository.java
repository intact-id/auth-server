package com.intact_id.orchestration.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationStepRepository extends JpaRepository<VerificationStep, String> {
    List<VerificationStep> findByVerificationRequestIdOrderByCreatedAtAsc(String verificationRequestId);
}
