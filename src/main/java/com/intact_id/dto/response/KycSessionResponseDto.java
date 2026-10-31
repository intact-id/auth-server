package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KycSessionResponseDto {
    private String sessionToken;
    private long expiresInSeconds;
    private String tier;
    private String environment;
    private String externalReference;
}
