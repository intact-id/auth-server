package com.intact_id.api.audit;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.UserActivityLogResponseDto;
import com.intact_id.dto.response.UserSessionResponseDto;
import com.intact_id.service.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Centralized user session and activity audit logs")
public class AuditController {

    private final AuditService auditService;

    @GetMapping("/api/admin/audit/sessions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List user sessions")
    public ResponseEntity<ApiResponse<Page<UserSessionResponseDto>>> getSessions(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserSessionResponseDto> sessions = auditService.getSessions(userId, companyId, status, fromDate, toDate, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<UserSessionResponseDto>>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage("Audit sessions retrieved successfully")
                .success(true)
                .data(sessions)
                .build());
    }

    @GetMapping("/api/admin/audit/sessions/{sessionId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get one session summary")
    public ResponseEntity<ApiResponse<UserSessionResponseDto>> getSession(@PathVariable String sessionId) {
        UserSessionResponseDto session = auditService.getSession(sessionId);
        return ResponseEntity.ok(ApiResponse.<UserSessionResponseDto>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage("Audit session retrieved successfully")
                .success(true)
                .data(session)
                .build());
    }

    @GetMapping("/api/admin/audit/activities")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List user activity logs")
    public ResponseEntity<ApiResponse<Page<UserActivityLogResponseDto>>> getActivities(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserActivityLogResponseDto> activities = auditService.getActivities(
                userId, companyId, sessionId, httpMethod, path, statusCode, activityType, fromDate, toDate, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<UserActivityLogResponseDto>>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage("Audit activities retrieved successfully")
                .success(true)
                .data(activities)
                .build());
    }

    @GetMapping("/api/admin/users/{userId}/logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get logs for a single user")
    public ResponseEntity<ApiResponse<Page<UserActivityLogResponseDto>>> getUserLogs(
            @PathVariable String userId,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String activityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<UserActivityLogResponseDto> activities = auditService.getActivities(
                userId, null, sessionId, httpMethod, path, statusCode, activityType, fromDate, toDate, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<UserActivityLogResponseDto>>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage("User audit logs retrieved successfully")
                .success(true)
                .data(activities)
                .build());
    }
}
