package com.intact_id.identity.web;

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
