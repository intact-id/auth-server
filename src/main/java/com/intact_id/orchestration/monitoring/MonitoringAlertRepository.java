package com.intact_id.orchestration.monitoring;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface MonitoringAlertRepository extends JpaRepository<MonitoringAlert, String> {

    @Query("SELECT a FROM MonitoringAlert a WHERE (:companyId IS NULL OR a.companyId = :companyId)"
            + " AND (:status IS NULL OR a.status = :status) ORDER BY a.createdAt DESC")
    Page<MonitoringAlert> search(@Param("companyId") String companyId, @Param("status") MonitoringAlert.Status status, Pageable page);

    List<MonitoringAlert> findBySubjectIdOrderByCreatedAtDesc(String subjectId);

    long countByCompanyIdAndStatus(String companyId, MonitoringAlert.Status status);

    long countByStatus(MonitoringAlert.Status status);
}
