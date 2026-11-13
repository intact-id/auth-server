package com.intact_id.accounts.web;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateLimitsResponseDto {
    private String companyId;
    private String companyName;
    private Integer perHour;
    private Integer perDay;
    private Integer perMonth;
    private Long totalRequests;
    private Long totalSuccessful;
    private Long totalFailed;
}
