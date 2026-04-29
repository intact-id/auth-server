package com.intact_id.dto.response.billing;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.constant.enums.billing.BillingMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CompanyBillingProfileResponseDto {
    private String id;
    private String companyId;
    private String billingTierCode;
    private BillingMode billingMode;
    private BigDecimal unitPrice;
    private String currency;
    private String billingEmail;
    private Boolean billOnSubmission;
    private Boolean billOnCompleted;
    private Boolean billOnRejected;
    private Boolean billOnFailedNonSystem;
    private Boolean billOnFailedSystem;
    private BigDecimal currentBalance;
    private BigDecimal lowBalanceThreshold;
    private BigDecimal creditLimit;
    private Integer invoiceDueDays;
    private GlobalStatuses status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
