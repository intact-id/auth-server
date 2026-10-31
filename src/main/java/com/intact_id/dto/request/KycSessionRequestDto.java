package com.intact_id.dto.request;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycSessionRequestDto {

    @NotBlank(message = "tier is required (tier1, tier2, tier3)")
    private String tier;

    @NotNull(message = "environment is required")
    private ApiKeyEnvironment environment;

    @Size(max = 255, message = "externalReference must be at most 255 characters")
    private String externalReference;
}
