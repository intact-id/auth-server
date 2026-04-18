package com.intact_id.dto.request;

import lombok.Data;

@Data
public class RateLimitsRequestDto {
    private EnvLimits dev;
    private EnvLimits prod;

    @Data
    public static class EnvLimits {
        private Integer perHour;
        private Integer perDay;
        private Integer perMonth;
    }
}
