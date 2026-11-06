package com.intact_id.dto.request.kyc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompleteUploadRequestDto {

    @NotBlank(message = "key is required")
    @Size(max = 512)
    private String key;
}
