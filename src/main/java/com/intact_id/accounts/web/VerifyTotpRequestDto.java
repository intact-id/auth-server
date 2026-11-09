package com.intact_id.accounts.web;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyTotpRequestDto {
    @NotNull(message = "Verification code is required")
    private Integer verificationCode;
}
