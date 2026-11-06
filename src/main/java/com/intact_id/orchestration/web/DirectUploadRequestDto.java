package com.intact_id.orchestration.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DirectUploadRequestDto {

    @NotBlank(message = "contentType is required")
    @Size(max = 100)
    private String contentType;

    @NotNull(message = "size is required")
    @Positive
    private Long size;
}
