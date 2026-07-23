package com.intact_id.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaLoginVerifyRequestDto {
    @NotBlank(message = "MFA token is required")
    private String mfaToken;

    // Exactly one of verificationCode / backupCode is expected.
    private Integer verificationCode;

    private String backupCode;

    private Boolean rememberDevice = false;
}
