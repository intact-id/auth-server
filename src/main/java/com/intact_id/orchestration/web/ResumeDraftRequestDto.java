package com.intact_id.orchestration.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeDraftRequestDto {

    @NotBlank(message = "resumeToken is required")
    @Size(max = 128)
    private String resumeToken;
}
