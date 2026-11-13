package com.intact_id.accounts.web;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyUsageResponseDto {
    private String companyId;
    private String companyName;
    private Long totalRequests;
    private Long totalSuccessfulRequests;
    private Long totalFailedRequests;
    private LocalDateTime lastUsedAt;
    private Integer rateLimitPerHour;
    private Integer rateLimitPerDay;
    private Integer rateLimitPerMonth;
}
