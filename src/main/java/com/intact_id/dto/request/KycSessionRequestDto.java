package com.intact_id.dto.request;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KycSessionRequestDto {

    @NotBlank(message = "tier is required (tier1, tier2, tier3)")
    private String tier;

    @NotNull(message = "environment is required")
    private ApiKeyEnvironment environment;
}
