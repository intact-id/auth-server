package com.intact_id.db.repository.billing;

import com.intact_id.db.model.billing.CompanyBillingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyBillingProfileRepository extends JpaRepository<CompanyBillingProfile, String> {
    Optional<CompanyBillingProfile> findByCompanyId(String companyId);
}
