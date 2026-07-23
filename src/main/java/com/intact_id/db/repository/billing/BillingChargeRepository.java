package com.intact_id.db.repository.billing;

import com.intact_id.constant.enums.billing.BillingChargeStatus;
import com.intact_id.db.model.billing.BillingCharge;
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
