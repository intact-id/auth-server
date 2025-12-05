package com.intact_id.db.repository.kyc;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.kyc.VerificationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, String> {

        Optional<VerificationRequest> findByIdAndCompanyId(String id, String companyId);

        Page<VerificationRequest> findByCompanyId(String companyId, Pageable pageable);

        Page<VerificationRequest> findByCompanyIdAndStatus(String companyId, GlobalStatuses status, Pageable pageable);

        @Query("SELECT v FROM VerificationRequest v WHERE (:companyId IS NULL OR v.company.id = :companyId) " +
                        "AND (:status IS NULL OR v.status = :status) " +
                        "AND (:tier IS NULL OR v.tier = :tier) " +
                        "AND (CAST(:fromDate AS timestamp) IS NULL OR v.createdAt >= :fromDate) " +
                        "AND (CAST(:toDate AS timestamp) IS NULL OR v.createdAt <= :toDate)")
        Page<VerificationRequest> findByFilters(
                        @Param("companyId") String companyId,
                        @Param("status") GlobalStatuses status,
                        @Param("tier") String tier,
                        @Param("fromDate") LocalDateTime fromDate,
                        @Param("toDate") LocalDateTime toDate,
                        Pageable pageable);

        long countByCompanyIdAndCreatedAtAfter(String companyId, LocalDateTime after);
}
