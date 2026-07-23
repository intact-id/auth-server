package com.intact_id.dto.response.billing;

import com.intact_id.constant.enums.billing.BillingInvoiceStatus;
import com.intact_id.constant.enums.billing.BillingMode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BillingInvoiceResponseDto {
    private String id;
    private String invoiceNumber;
    private String companyId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BillingMode billingMode;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal total;
    private BigDecimal amountPaid;
    private BillingInvoiceStatus status;
    private String recipientEmail;
    private LocalDate dueAt;
    private LocalDateTime generatedAt;
    private LocalDateTime sentAt;
    private List<BillingInvoiceLineResponseDto> lineItems;
}
