package com.intact_id.service.audit;

import com.intact_id.db.model.Company;
import com.intact_id.db.model.user.RefreshToken;
import com.intact_id.db.model.user.User;
import com.intact_id.db.model.user.UserActivityLog;
import com.intact_id.db.model.user.UserSession;
import com.intact_id.db.repository.user.UserActivityLogRepository;
import com.intact_id.db.repository.user.UserSessionRepository;
import com.intact_id.dto.response.UserActivityLogResponseDto;
import com.intact_id.dto.response.UserSessionResponseDto;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final UserSessionRepository userSessionRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    @Override
    @Transactional
    public void startSession(String sessionId, User user, RefreshToken refreshToken, String ipAddress, String userAgent) {
        UserSession session = new UserSession();
        session.setSessionId(sessionId);
        session.setUser(user);
        session.setCompany(user.getCompany());
        session.setRefreshToken(refreshToken);
        session.setLoginAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());
        session.setStatus("ACTIVE");
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        userSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void recordLogin(String sessionId, User user, String ipAddress) {
        recordActivity(sessionId, user, "LOGIN", "POST", "/api/auth/login", null,
                200, LocalDateTime.now(), LocalDateTime.now(), 0L, "AUTH", sessionId, ipAddress);
    }

    @Override
    @Transactional
    public void linkRefreshToken(String sessionId, RefreshToken refreshToken) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setRefreshToken(refreshToken);
            session.setLastActivityAt(LocalDateTime.now());
            userSessionRepository.save(session);
        });
    }

    @Override
    @Transactional
    public void recordTokenRefresh(String sessionId, User user, String ipAddress) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastActivityAt(LocalDateTime.now());
            userSessionRepository.save(session);
        });
        recordActivity(sessionId, user, "TOKEN_REFRESH", "POST", "/api/auth/refresh", null,
                200, LocalDateTime.now(), LocalDateTime.now(), 0L, "AUTH", sessionId, ipAddress);
    }

    @Override
    @Transactional
    public void recordRequestActivity(String sessionId, User user, String httpMethod, String path, String queryString,
            Integer statusCode, LocalDateTime startedAt, LocalDateTime completedAt, Long durationMs,
            String resourceType, String resourceId, String ipAddress) {
        recordActivity(sessionId, user, "API_REQUEST", httpMethod, path, queryString,
                statusCode, startedAt, completedAt, durationMs, resourceType, resourceId, ipAddress);
    }

    @Override
    @Transactional
    public void closeSession(String sessionId, User user, String ipAddress) {
        userSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            LocalDateTime now = LocalDateTime.now();
            session.setLogoutAt(now);
            session.setLastActivityAt(now);
            session.setStatus("CLOSED");
            userSessionRepository.save(session);
        });
        recordActivity(sessionId, user, "LOGOUT", "POST", "/api/auth/logout", null,
                200, LocalDateTime.now(), LocalDateTime.now(), 0L, "AUTH", sessionId, ipAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSessionResponseDto> getSessions(String userId, String companyId, String status,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        Specification<UserSession> specification = Specification.where(hasUserId(userId))
                .and(hasCompanyId(companyId))
                .and(hasStatus(status))
                .and(loginAtFrom(fromDate))
                .and(loginAtTo(toDate));

        return userSessionRepository.findAll(specification, pageable)
                .map(this::toSessionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSessionResponseDto getSession(String sessionId) {
        return userSessionRepository.findBySessionId(sessionId)
                .map(this::toSessionResponse)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Session not found: " + sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityLogResponseDto> getActivities(String userId, String companyId, String sessionId,
            String httpMethod, String path, Integer statusCode, String activityType,
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        Specification<UserActivityLog> specification = Specification.where(hasActivityUserId(userId))
                .and(hasActivityCompanyId(companyId))
                .and(hasSessionId(sessionId))
                .and(hasHttpMethod(httpMethod))
                .and(hasPath(path))
                .and(hasStatusCode(statusCode))
                .and(hasActivityType(activityType))
                .and(activityStartedFrom(fromDate))
                .and(activityStartedTo(toDate));

        return userActivityLogRepository.findAll(specification, pageable)
                .map(this::toActivityResponse);
    }

    private void recordActivity(String sessionId, User user, String activityType, String httpMethod,
            String path, String queryString, Integer statusCode, LocalDateTime startedAt,
            LocalDateTime completedAt, Long durationMs, String resourceType, String resourceId, String ipAddress) {
        UserSession session = userSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Session not found: " + sessionId));

        session.setLastActivityAt(completedAt != null ? completedAt : LocalDateTime.now());
        userSessionRepository.save(session);

        UserActivityLog activityLog = new UserActivityLog();
        activityLog.setSession(session);
        activityLog.setUser(user);
        activityLog.setCompany(user.getCompany());
        activityLog.setActivityType(activityType);
        activityLog.setHttpMethod(httpMethod);
        activityLog.setPath(path);
        activityLog.setQueryString(queryString);
        activityLog.setStatusCode(statusCode);
        activityLog.setStartedAt(startedAt);
        activityLog.setCompletedAt(completedAt);
        activityLog.setDurationMs(durationMs);
        activityLog.setResourceType(resourceType);
        activityLog.setResourceId(resourceId);
        activityLog.setIpAddress(ipAddress);
        userActivityLogRepository.save(activityLog);
    }

    private UserSessionResponseDto toSessionResponse(UserSession session) {
        Company company = session.getCompany();
        return UserSessionResponseDto.builder()
                .id(session.getId())
                .sessionId(session.getSessionId())
                .userId(session.getUser().getId())
                .username(session.getUser().getUsername())
                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getLegalName() : null)
                .loginAt(session.getLoginAt())
                .logoutAt(session.getLogoutAt())
                .lastActivityAt(session.getLastActivityAt())
                .status(session.getStatus())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .activityCount(userActivityLogRepository.countBySession_SessionId(session.getSessionId()))
                .build();
    }

    private UserActivityLogResponseDto toActivityResponse(UserActivityLog activityLog) {
        Company company = activityLog.getCompany();
        return UserActivityLogResponseDto.builder()
                .id(activityLog.getId())
                .sessionId(activityLog.getSession().getSessionId())
                .userId(activityLog.getUser().getId())
                .username(activityLog.getUser().getUsername())
                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getLegalName() : null)
                .activityType(activityLog.getActivityType())
                .httpMethod(activityLog.getHttpMethod())
                .path(activityLog.getPath())
                .queryString(activityLog.getQueryString())
                .statusCode(activityLog.getStatusCode())
                .startedAt(activityLog.getStartedAt())
                .completedAt(activityLog.getCompletedAt())
                .durationMs(activityLog.getDurationMs())
                .resourceType(activityLog.getResourceType())
                .resourceId(activityLog.getResourceId())
                .ipAddress(activityLog.getIpAddress())
                .build();
    }

    private Specification<UserSession> hasUserId(String userId) {
        return (root, query, cb) -> userId == null || userId.isBlank() ? null : cb.equal(root.get("user").get("id"), userId);
    }

    private Specification<UserSession> hasCompanyId(String companyId) {
        return (root, query, cb) -> companyId == null || companyId.isBlank() ? null : cb.equal(root.get("company").get("id"), companyId);
    }

    private Specification<UserSession> hasStatus(String status) {
        return (root, query, cb) -> status == null || status.isBlank() ? null
                : cb.equal(cb.upper(root.get("status")), status.trim().toUpperCase(Locale.ROOT));
    }

    private Specification<UserSession> loginAtFrom(LocalDateTime fromDate) {
        return (root, query, cb) -> fromDate == null ? null : cb.greaterThanOrEqualTo(root.get("loginAt"), fromDate);
    }

    private Specification<UserSession> loginAtTo(LocalDateTime toDate) {
        return (root, query, cb) -> toDate == null ? null : cb.lessThanOrEqualTo(root.get("loginAt"), toDate);
    }

    private Specification<UserActivityLog> hasActivityUserId(String userId) {
        return (root, query, cb) -> userId == null || userId.isBlank() ? null : cb.equal(root.get("user").get("id"), userId);
    }

    private Specification<UserActivityLog> hasActivityCompanyId(String companyId) {
        return (root, query, cb) -> companyId == null || companyId.isBlank() ? null : cb.equal(root.get("company").get("id"), companyId);
    }

    private Specification<UserActivityLog> hasSessionId(String sessionId) {
        return (root, query, cb) -> sessionId == null || sessionId.isBlank() ? null : cb.equal(root.get("session").get("sessionId"), sessionId);
    }

    private Specification<UserActivityLog> hasHttpMethod(String httpMethod) {
        return (root, query, cb) -> httpMethod == null || httpMethod.isBlank() ? null
                : cb.equal(cb.upper(root.get("httpMethod")), httpMethod.trim().toUpperCase(Locale.ROOT));
    }

    private Specification<UserActivityLog> hasPath(String path) {
        return (root, query, cb) -> path == null || path.isBlank() ? null
                : cb.like(cb.lower(root.get("path")), "%" + path.trim().toLowerCase(Locale.ROOT) + "%");
    }

    private Specification<UserActivityLog> hasStatusCode(Integer statusCode) {
        return (root, query, cb) -> statusCode == null ? null : cb.equal(root.get("statusCode"), statusCode);
    }

    private Specification<UserActivityLog> hasActivityType(String activityType) {
        return (root, query, cb) -> activityType == null || activityType.isBlank() ? null
                : cb.equal(cb.upper(root.get("activityType")), activityType.trim().toUpperCase(Locale.ROOT));
    }

    private Specification<UserActivityLog> activityStartedFrom(LocalDateTime fromDate) {
        return (root, query, cb) -> fromDate == null ? null : cb.greaterThanOrEqualTo(root.get("startedAt"), fromDate);
    }

    private Specification<UserActivityLog> activityStartedTo(LocalDateTime toDate) {
        return (root, query, cb) -> toDate == null ? null : cb.lessThanOrEqualTo(root.get("startedAt"), toDate);
    }
}
