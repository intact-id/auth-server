package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RateLimitsResponseDto {
    private String companyId;
    private String companyName;
    private EnvLimits dev;
    private EnvLimits prod;

    @Data
    @Builder
    public static class EnvLimits {
        private Integer perHour;
        private Integer perDay;
        private Integer perMonth;
        private Long totalRequests;
        private Long totalSuccessful;
        private Long totalFailed;
    }
}
