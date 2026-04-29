package com.intact_id.db.repository;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyApplicationApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 12:42
 */

public interface CompanyApplicationApprovalRepository extends JpaRepository<CompanyApplicationApproval,String> {
    boolean existsByApplicationIdAndPerformedBy(String applicationId, String performedBy);

    long countByApplicationId(String applicationId);

    Page<CompanyApplicationApproval> findByFinalActionIsNullAndPerformedByAction(
            GlobalStatuses performedByAction,
            Pageable pageable
    );
}
