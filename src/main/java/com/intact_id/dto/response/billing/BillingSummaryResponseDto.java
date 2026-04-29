package com.intact_id.dto.response.billing;

import com.intact_id.constant.enums.billing.BillingMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BillingSummaryResponseDto {
    private String companyId;
    private String billingTierCode;
    private BillingMode billingMode;
    private String currency;
    private BigDecimal unitPrice;
    private BigDecimal currentBalance;
    private BigDecimal walletBalance;
    private BigDecimal usedCredit;
    private BigDecimal remainingCredit;
    private BigDecimal availableToSpend;
    private BigDecimal outstandingAmount;
    private BigDecimal readyToInvoiceAmount;
    private BigDecimal debitedAmount;
    private BigDecimal invoicedAmount;
    private Integer estimatedSubmissionsRemaining;
}
