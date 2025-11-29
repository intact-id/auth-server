package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyGenerationResponseDto {
    private String apiKey; // The raw API key (only shown once)
    private String apiSecret; // The raw API secret (only shown once)
    private LocalDateTime expiresAt;
    private String status;
}
