package com.intact_id.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserActivityLogResponseDto {
    private String id;
    private String sessionId;
    private String userId;
    private String username;
    private String companyId;
    private String companyName;
    private String activityType;
    private String httpMethod;
    private String path;
    private String queryString;
    private Integer statusCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long durationMs;
    private String resourceType;
    private String resourceId;
    private String ipAddress;
}
