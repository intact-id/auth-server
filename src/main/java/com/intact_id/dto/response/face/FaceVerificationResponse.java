package com.intact_id.dto.response.face;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class FaceVerificationResponse {
    @JsonProperty("match")
    private boolean verified;
    @JsonProperty("confidence")
    private Double confidence;
    private double distance;
    @JsonProperty("threshold")
    private double threshold;
    @JsonProperty("review_status")
    private String reviewStatus;
    @JsonProperty("review_reason")
    private String reviewReason;
    @JsonProperty("model")
    private String model;
    @JsonProperty("similarity_metric")
    private String similarityMetric;
    @JsonProperty("facial_areas")
    private Map<String, Object> facialAreas;
    @JsonProperty("duration_ms")
    private double time;
}
