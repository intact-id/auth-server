package com.intact_id.billing.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillingChargeRepository extends JpaRepository<BillingCharge, String> {
    Optional<BillingCharge> findByVerificationRequestId(String verificationRequestId);

    List<BillingCharge> findByCompanyIdAndStatusInAndChargeableAtBetween(
            String companyId,
            List<BillingChargeStatus> statuses,
            LocalDateTime from,
            LocalDateTime to);
}
