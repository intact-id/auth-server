package com.intact_id.workflow.web;

import com.intact_id.workflow.ServiceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ServiceStatusRequestDto {

    @NotNull(message = "status is required")
    private ServiceStatus status;
}
