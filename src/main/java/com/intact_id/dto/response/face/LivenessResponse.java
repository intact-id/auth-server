package com.intact_id.dto.response.face;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class LivenessResponse {

    @JsonProperty("liveness_score")
    private int livenessScore;

    @JsonProperty("passed")
    private boolean passed;

    @JsonProperty("checks")
    private Map<String, Object> checks;

    @JsonProperty("issues")
    private List<String> issues;

    @JsonProperty("recommendation")
    private String recommendation;

    @JsonProperty("duration_ms")
    private double durationMs;
}
