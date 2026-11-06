package com.intact_id.orchestration.web;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class DirectUploadResponseDto {
    private String key;
    private String uploadUrl;
    private String method;
    // Send exactly these headers with the PUT
    private Map<String, String> headers;
    private LocalDateTime expiresAt;
}
