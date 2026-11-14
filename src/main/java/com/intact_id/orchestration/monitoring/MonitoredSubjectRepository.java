package com.intact_id.orchestration.monitoring;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface MonitoredSubjectRepository extends JpaRepository<MonitoredSubject, String> {

    @Query("SELECT s FROM MonitoredSubject s WHERE s.status = com.intact_id.orchestration.monitoring.MonitoredSubject.Status.ACTIVE"
            + " AND s.nextScreenAt <= :now ORDER BY s.nextScreenAt ASC")
    List<MonitoredSubject> findDue(@Param("now") LocalDateTime now, Pageable page);

    @Query("SELECT s FROM MonitoredSubject s WHERE (:companyId IS NULL OR s.companyId = :companyId)"
            + " AND (:status IS NULL OR s.status = :status)"
            + " AND (:search IS NULL OR LOWER(s.displayName) LIKE LOWER(CONCAT('%', :search, '%')) OR s.externalReference = :search)"
            + " ORDER BY s.openAlerts DESC, s.createdAt DESC")
    Page<MonitoredSubject> search(@Param("companyId") String companyId, @Param("status") MonitoredSubject.Status status,
                                  @Param("search") String search, Pageable page);

    List<MonitoredSubject> findByVerificationId(String verificationId);

    Optional<MonitoredSubject> findByVerificationIdAndPartyIdIsNull(String verificationId);

    Optional<MonitoredSubject> findByVerificationIdAndPartyId(String verificationId, String partyId);

    long countByCompanyIdAndStatus(String companyId, MonitoredSubject.Status status);
}
