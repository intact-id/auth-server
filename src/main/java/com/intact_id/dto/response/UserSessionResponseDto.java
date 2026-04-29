package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserSessionResponseDto {
    private String id;
    private String sessionId;
    private String userId;
    private String username;
    private String companyId;
    private String companyName;
    private LocalDateTime loginAt;
    private LocalDateTime logoutAt;
    private LocalDateTime lastActivityAt;
    private String status;
    private String ipAddress;
    private String userAgent;
    private long activityCount;
}
