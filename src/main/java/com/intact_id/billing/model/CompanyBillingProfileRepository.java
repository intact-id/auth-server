package com.intact_id.billing.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyBillingProfileRepository extends JpaRepository<CompanyBillingProfile, String> {
    Optional<CompanyBillingProfile> findByCompanyId(String companyId);
}
