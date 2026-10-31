package com.intact_id.dto.request.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeDraftRequestDto {

    @NotBlank(message = "resumeToken is required")
    @Size(max = 128)
    private String resumeToken;
}
