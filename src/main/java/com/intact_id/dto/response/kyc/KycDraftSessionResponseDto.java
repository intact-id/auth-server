package com.intact_id.dto.response.kyc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycDraftSessionResponseDto {
    // Null when the draft is no longer editable (e.g. already submitted)
    private String draftToken;
    private Long draftTokenExpiresInSeconds;
    // Only returned when the draft is created; store it in localStorage
    private String resumeToken;
    private KycDraftResponseDto draft;
}
