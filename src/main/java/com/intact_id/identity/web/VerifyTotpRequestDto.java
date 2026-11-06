package com.intact_id.identity.web;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyTotpRequestDto {
    @NotNull(message = "Verification code is required")
    private Integer verificationCode;
}
