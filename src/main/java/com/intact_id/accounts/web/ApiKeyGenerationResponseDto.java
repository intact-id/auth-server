package com.intact_id.accounts.web;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyGenerationResponseDto {
    private String apiKey;
    private String apiSecret;
    private LocalDateTime expiresAt;
    private String status;
}
