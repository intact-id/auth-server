package com.intact_id.accounts.web;

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
