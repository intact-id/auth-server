package com.intact_id.security.filter;

import com.intact_id.db.model.user.User;
import com.intact_id.service.audit.AuditService;
import com.intact_id.service.jwt.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityAuditFilter extends OncePerRequestFilter {

    private static final Pattern VERIFICATION_PATTERN = Pattern.compile("/api/v1/kyc/(?:dev/|prod/)?verify/([^/]+)");
    private static final Pattern USER_PATTERN = Pattern.compile("/api/admin/users/([^/]+)");
    private static final Pattern COMPANY_PATTERN = Pattern.compile("/api/companies/([^/]+)");

    private final JwtService jwtService;
    private final AuditService auditService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        LocalDateTime startedAt = LocalDateTime.now();
        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                logRequestActivity(request, response, startedAt, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.error("Failed to write user activity audit log", e);
            }
        }
    }

    private void logRequestActivity(HttpServletRequest request, HttpServletResponse response,
            LocalDateTime startedAt, long durationMs) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            return;
        }

        String sessionId = jwtService.extractSessionId(authHeader.substring(7));
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String path = request.getRequestURI();
        if ("/api/auth/current-user".equals(path) || "/api/auth/logout".equals(path)) {
            return;
        }

        ResolvedResource resource = resolveResource(path);
        auditService.recordRequestActivity(
                sessionId,
                user,
                request.getMethod(),
                path,
                request.getQueryString(),
                response.getStatus(),
                startedAt,
                LocalDateTime.now(),
                durationMs,
                resource.resourceType(),
                resource.resourceId(),
                getClientIp(request));
    }

    private ResolvedResource resolveResource(String path) {
        Matcher verificationMatcher = VERIFICATION_PATTERN.matcher(path);
        if (verificationMatcher.find()) {
            return new ResolvedResource("VERIFICATION", verificationMatcher.group(1));
        }

        Matcher userMatcher = USER_PATTERN.matcher(path);
        if (userMatcher.find()) {
            return new ResolvedResource("USER", userMatcher.group(1));
        }

        Matcher companyMatcher = COMPANY_PATTERN.matcher(path);
        if (companyMatcher.find()) {
            return new ResolvedResource("COMPANY", companyMatcher.group(1));
        }

        return new ResolvedResource("ENDPOINT", path);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record ResolvedResource(String resourceType, String resourceId) {
    }
}
