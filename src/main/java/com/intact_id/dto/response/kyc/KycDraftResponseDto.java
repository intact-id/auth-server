package com.intact_id.dto.response.kyc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycDraftResponseDto {
    private String draftId;
    private String status;
    private String tier;
    private String environment;
    private LocalDateTime expiresAt;
    private String verificationId;
    // Masked values only; raw PII and document content are never returned
    private Map<String, String> details;
    private List<String> documents;
    private HandoffSummary handoff;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HandoffSummary {
        private String handoffId;
        private String status;
        private LocalDateTime expiresAt;
        private LocalDateTime claimedAt;
    }
}
