package com.intact_id.dto.response.kyc;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KycHandoffResponseDto {
    private String handoffId;
    // Render as QR / copy link; the code is in the URL fragment so it never reaches server logs
    private String url;
    private LocalDateTime expiresAt;
}
