package com.intact_id.dto.response.billing;

import com.intact_id.constant.enums.billing.WalletTransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WalletTransactionResponseDto {
    private String id;
    private WalletTransactionType type;
    private BigDecimal amount;
    private String currency;
    private String reference;
    private String description;
    private String verificationRequestId;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
