package com.intact_id.checks.screening;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Wire shapes of sactions-service. Enums stay strings here so a new value there never breaks decoding. */
public final class ScreeningApi {

    private ScreeningApi() {
    }

    public record PersonMatch(String firstName, String lastName, String fatherName, String birthDate) {
    }

    public record BusinessMatch(String name, String jurisdiction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(String verdict, List<Match> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Match(String entityId, String caption, double score, List<String> datasets, List<String> topics,
                 List<String> sourceUrls) {
    }

    public record AssessmentRequest(String tenantId, String customerId, String customerType, String stage,
                             Map<String, Object> attributePayload, String triggeredBy,
                             PersonMatch personIdentity, BusinessMatch businessIdentity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Assessment(String id, String riskModelId, Integer modelVersion, String status, BigDecimal overallScore,
                      String overallLevel, Boolean overrideApplied, Result screening) {
    }

    public record TenantCreation(String tenantIdentifier, String tenantType, String tenantName, String description,
                          Map<String, Object> extraDetails) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tenant(String id, String tenantIdentifier) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(T data, String status, String message) {
    }
}
