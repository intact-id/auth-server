package com.intact_id.db.repository;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 19:07
 */

public interface CompanyApplicationRepository extends JpaRepository<CompanyApplication, String> {
    Page<CompanyApplication> findByApplicationStatus(GlobalStatuses applicationStatus, Pageable pageable);

    boolean existsByEmailIgnoreCase(String email);

    Optional<CompanyApplication> findByApplicationNumber(String applicationId);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    boolean existsByEmailIgnoreCaseOrRegistrationNumberIgnoreCase(String email, String registrationNumber);
}
