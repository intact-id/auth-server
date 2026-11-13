package com.intact_id.orchestration.web;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KycSessionResponseDto {
    private String sessionToken;
    private long expiresInSeconds;
    private String tier;
    private String workflow;
    private Integer workflowVersion;
    private String externalReference;
}
