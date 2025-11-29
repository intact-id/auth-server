package com.intact_id.dto.response.face;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class FaceVerificationResponse {
    private boolean verified;
    private double distance;
    @JsonProperty("threshold")
    private double threshold;
    @JsonProperty("model")
    private String model;
    @JsonProperty("similarity_metric")
    private String similarityMetric;
    @JsonProperty("facial_areas")
    private Map<String, Object> facialAreas;
    private double time;
}
