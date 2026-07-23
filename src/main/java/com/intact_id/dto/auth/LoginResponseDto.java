package com.intact_id.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponseDto {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private Long refreshExpiresIn;

    // Populated instead of the token fields above when a second factor is required.
    private Boolean mfaRequired;
    private String mfaToken;
    private Long mfaTokenExpiresIn;

    // Populated only when a login (password or MFA-verify) requested "remember this device".
    private String deviceToken;
}
