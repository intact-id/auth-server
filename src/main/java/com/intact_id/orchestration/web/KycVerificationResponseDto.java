package com.intact_id.orchestration.web;

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
