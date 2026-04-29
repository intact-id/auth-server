package com.intact_id.service.audit;

import com.intact_id.db.model.user.RefreshToken;
import com.intact_id.db.model.user.User;
import com.intact_id.dto.response.UserActivityLogResponseDto;
import com.intact_id.dto.response.UserSessionResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AuditService {

    void startSession(String sessionId, User user, RefreshToken refreshToken, String ipAddress, String userAgent);

    void recordLogin(String sessionId, User user, String ipAddress);

    void linkRefreshToken(String sessionId, RefreshToken refreshToken);

    void recordTokenRefresh(String sessionId, User user, String ipAddress);

    void recordRequestActivity(String sessionId, User user, String httpMethod, String path, String queryString,
            Integer statusCode, LocalDateTime startedAt, LocalDateTime completedAt, Long durationMs,
            String resourceType, String resourceId, String ipAddress);

    void closeSession(String sessionId, User user, String ipAddress);

    Page<UserSessionResponseDto> getSessions(String userId, String companyId, String status,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable);

    UserSessionResponseDto getSession(String sessionId);

    Page<UserActivityLogResponseDto> getActivities(String userId, String companyId, String sessionId,
            String httpMethod, String path, Integer statusCode, String activityType,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable);
}
