package com.intact_id.accounts.web;

import lombok.Data;

@Data
public class RateLimitsRequestDto {
    // null = leave as is; a negative value removes the limit
    private Integer perHour;
    private Integer perDay;
    private Integer perMonth;
}
