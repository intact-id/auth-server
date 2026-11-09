package com.intact_id.orchestration.web;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class KycSessionRequestDto {

    // Required unless a workflow is given; with a workflow it is kept for reporting only
    private String tier;

    // Workflow key (e.g. full-onboarding): sandbox runs its latest published version, production its live one
    private String workflow;

    @NotNull(message = "environment is required")
    private ApiKeyEnvironment environment;

    @Size(max = 255, message = "externalReference must be at most 255 characters")
    private String externalReference;
}
