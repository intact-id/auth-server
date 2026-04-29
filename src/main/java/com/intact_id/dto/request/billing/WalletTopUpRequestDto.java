package com.intact_id.dto.request.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletTopUpRequestDto {
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", inclusive = true, message = "amount must be > 0")
    private BigDecimal amount;

    private String currency = "USD";
    private String reference;
    private String description;
}
