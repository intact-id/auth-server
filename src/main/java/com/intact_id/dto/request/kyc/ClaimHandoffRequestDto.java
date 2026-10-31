package com.intact_id.dto.request.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClaimHandoffRequestDto {

    @NotBlank(message = "code is required")
    @Size(max = 64)
    private String code;
}
