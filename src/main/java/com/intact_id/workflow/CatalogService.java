package com.intact_id.workflow;

import java.util.List;
import java.util.Map;

/**
 * One service a workflow can include. code matches the billing-service charge code.
 *
 * @param requires       services that must run before this one
 * @param requiredFields applicant fields it needs (firstName, dateOfBirth, ...)
 * @param captures       uploads it needs (id_front, selfie, liveness_frames, ...)
 * @param outputs        result fields conditions can test, e.g. {key: "liveness.score", type: "number"}
 * @param countries      applicant countries it supports (ISO3); empty means all
 * @param ui             hints for the widget and builder, e.g. {screen: "document_capture", icon: "id-card"}
 * @param settingsSchema JSON Schema for the per-step settings a workflow may set
 */
public record CatalogService(
        String code,
        String name,
        String description,
        ServiceCategory category,
        ServiceStatus status,
        List<SubjectType> subjectTypes,
        List<String> countries,
        List<String> requires,
        List<String> requiredFields,
        List<String> captures,
        List<Map<String, Object>> outputs,
        Map<String, Object> ui,
        Map<String, Object> settingsSchema,
        int sortOrder) {

    public boolean supportsCountry(String country) {
        return country == null || countries.isEmpty() || countries.contains(country);
    }

    public boolean supportsSubject(SubjectType subjectType) {
        return subjectType == null || subjectTypes.isEmpty() || subjectTypes.contains(subjectType);
    }
}
