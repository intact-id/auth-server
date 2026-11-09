package com.intact_id.workflow.web;

import com.intact_id.workflow.SubjectType;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RequirementsRequestDto {

    @NotEmpty(message = "services is required")
    private List<String> services;

    private SubjectType subjectType;

    private String country;
}
