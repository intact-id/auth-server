package com.intact_id.workflow.web;

import com.intact_id.workflow.ServiceCategory;
import com.intact_id.workflow.SubjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Create (code required) or update (code ignored; omitted fields are kept). */
@Data
public class CatalogServiceRequestDto {

    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$", message = "code must be UPPER_SNAKE_CASE and match the billing service charge code")
    private String code;

    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    private ServiceCategory category;

    private List<SubjectType> subjectTypes;

    private List<@Pattern(regexp = "^[A-Z]{3}$", message = "countries are ISO 3166 alpha-3 codes") String> countries;

    private List<@NotBlank String> requires;

    private List<@NotBlank String> requiredFields;

    private List<@NotBlank String> captures;

    private List<Map<String, Object>> outputs;

    private Map<String, Object> ui;

    private Map<String, Object> settingsSchema;

    private Integer sortOrder;
}
