package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiKeyUsageResponseDto {
    private String companyId;
    private String companyName;

    private Long devTotalRequests;
    private Long devTotalSuccessfulRequests;
    private Long devTotalFailedRequests;
    private LocalDateTime devLastUsedAt;
    private Integer devRateLimitPerHour;
    private Integer devRateLimitPerDay;
    private Integer devRateLimitPerMonth;

    private Long prodTotalRequests;
    private Long prodTotalSuccessfulRequests;
    private Long prodTotalFailedRequests;
    private LocalDateTime prodLastUsedAt;
    private Integer prodRateLimitPerHour;
    private Integer prodRateLimitPerDay;
    private Integer prodRateLimitPerMonth;
}
