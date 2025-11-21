package com.intact_id.dto.response;

import com.intact_id.constant.enums.GlobalStatuses;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 19:22
 */


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApplicationCreationResponseDto {

    @Schema(description = "Unique identifier for the created application", example = "APP-2025-001234")
    private String applicationNumber;

    @Schema(description = "Current status of the application after creation", example = "PENDING_REVIEW")
    private GlobalStatuses status;

    @Schema(description = "Timestamp when the application was submitted")
    private LocalDateTime submittedAt;

    @Schema(description = "Friendly message for the applicant", example = "Your application has been received. We will notify you of any updates.")
    private String message;
}

