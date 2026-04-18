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

        Optional<VerificationRequest> findByIdAndCompanyIdAndEnvironment(String id, String companyId, com.intact_id.constant.enums.ApiKeyEnvironment environment);

        Optional<VerificationRequest> findByIdAndEnvironment(String id, com.intact_id.constant.enums.ApiKeyEnvironment environment);

        Page<VerificationRequest> findByCompanyId(String companyId, Pageable pageable);

        Page<VerificationRequest> findByCompanyIdAndStatus(String companyId, GlobalStatuses status, Pageable pageable);

        @Query(value = "SELECT v.* FROM verification_requests v " +
                        "LEFT JOIN company_tbl c ON c.id = v.company_id " +
                        "WHERE (:companyId IS NULL OR v.company_id = :companyId) " +
                        "AND (:environment IS NULL OR v.environment = :environment) " +
                        "AND (:status IS NULL OR v.status = :status) " +
                        "AND (:tier IS NULL OR v.tier = :tier) " +
                        "AND (CAST(:fromDate AS timestamp) IS NULL OR v.created_at >= :fromDate) " +
                        "AND (CAST(:toDate AS timestamp) IS NULL OR v.created_at <= :toDate) " +
                        "AND (:search IS NULL OR (" +
                        "LOWER(v.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(c.legal_name, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(v.overall_decision, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                        "LOWER(COALESCE(v.customer_data::text, '')) LIKE LOWER(CONCAT('%', :search, '%'))" +
                        ")) " +
                        "ORDER BY v.created_at DESC",
                        countQuery = "SELECT COUNT(*) FROM verification_requests v " +
                                        "LEFT JOIN company_tbl c ON c.id = v.company_id " +
                                        "WHERE (:companyId IS NULL OR v.company_id = :companyId) " +
                                        "AND (:environment IS NULL OR v.environment = :environment) " +
                                        "AND (:status IS NULL OR v.status = :status) " +
                                        "AND (:tier IS NULL OR v.tier = :tier) " +
                                        "AND (CAST(:fromDate AS timestamp) IS NULL OR v.created_at >= :fromDate) " +
                                        "AND (CAST(:toDate AS timestamp) IS NULL OR v.created_at <= :toDate) " +
                                        "AND (:search IS NULL OR (" +
                                        "LOWER(v.id) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                                        "LOWER(COALESCE(c.legal_name, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                                        "LOWER(COALESCE(v.overall_decision, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
                                        "LOWER(COALESCE(v.customer_data::text, '')) LIKE LOWER(CONCAT('%', :search, '%'))" +
                                        "))",
                        nativeQuery = true)
        Page<VerificationRequest> findByFilters(
                        @Param("companyId") String companyId,
                        @Param("environment") String environment,
                        @Param("status") String status,
                        @Param("tier") String tier,
                        @Param("fromDate") LocalDateTime fromDate,
                        @Param("toDate") LocalDateTime toDate,
                        @Param("search") String search,
                        Pageable pageable);

        long countByCompanyIdAndCreatedAtAfter(String companyId, LocalDateTime after);

        long countByCompanyIdAndEnvironmentAndStatusNotAndCreatedAtAfter(
                String companyId,
                com.intact_id.constant.enums.ApiKeyEnvironment environment,
                GlobalStatuses excludedStatus,
                LocalDateTime after);

        @Query("SELECT v FROM VerificationRequest v WHERE v.status = :status ORDER BY v.createdAt ASC")
        List<VerificationRequest> findByStatusOrderByCreatedAtAsc(
                        @Param("status") GlobalStatuses status, Pageable pageable);
}
