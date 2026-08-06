package com.intact_id.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyAccessTokenResponseDto {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private String scope;
}
