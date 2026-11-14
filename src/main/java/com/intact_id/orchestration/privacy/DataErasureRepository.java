package com.intact_id.orchestration.privacy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DataErasureRepository extends JpaRepository<DataErasure, String> {

    List<DataErasure> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable page);

    List<DataErasure> findAllByOrderByCreatedAtDesc(Pageable page);

    List<DataErasure> findByVerificationIdOrderByCreatedAtAsc(String verificationId);
}
