package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KycVerificationResponseDto {
    private String verificationId;
    private String externalReference;
    private String status;
    private String tier;
    private LocalDateTime estimatedCompletion;
    private String webhookUrl;
    private String message;
}
