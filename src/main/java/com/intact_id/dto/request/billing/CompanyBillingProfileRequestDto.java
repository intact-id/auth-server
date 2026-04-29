package com.intact_id.dto.request.billing;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.constant.enums.billing.BillingMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CompanyBillingProfileRequestDto {
    @NotBlank(message = "billing tier code is required")
    private String billingTierCode;

    @NotNull(message = "billing mode is required")
    private BillingMode billingMode;

    @NotNull(message = "unit price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "unit price must be >= 0")
    private BigDecimal unitPrice;

    private String currency = "USD";
    private String billingEmail;
    private Boolean billOnSubmission = false;
    private Boolean billOnCompleted = true;
    private Boolean billOnRejected = true;
    private Boolean billOnFailedNonSystem = true;
    private Boolean billOnFailedSystem = false;
    private BigDecimal lowBalanceThreshold = BigDecimal.ZERO;
    private BigDecimal creditLimit = BigDecimal.ZERO;
    private Integer invoiceDueDays = 30;
    private GlobalStatuses status = GlobalStatuses.ACTIVE;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
