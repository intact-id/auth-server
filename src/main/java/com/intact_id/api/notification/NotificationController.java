package com.intact_id.api.notification;

import com.intact_id.dto.request.NotificationRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.NotificationResponseDto;
import com.intact_id.service.NotificationService.NotificationService;
import com.intact_id.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

        private final NotificationService notificationService;

        @PostMapping
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<NotificationResponseDto>> createNotification(
                        @Valid @RequestBody NotificationRequestDto request) {
                String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(request.getCompanyId());
                request.setCompanyId(resolvedCompanyId);
                log.info("Received request to create notification for company: {}", resolvedCompanyId);

                NotificationResponseDto notification = notificationService.createNotification(request);

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.<NotificationResponseDto>builder()
                                                .success(true)
                                                .responseMessage("Notification created successfully")
                                                .data(notification)
                                                .build());
        }

        @GetMapping
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getAllNotifications(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                log.info("Fetching all notifications - page: {}, size: {}", page, size);

                Pageable pageable = PageRequest.of(page, size);
                Page<NotificationResponseDto> notifications;
                if (SecurityContextUtil.isSuperAdminAuthenticated()) {
                        notifications = notificationService.getAllNotifications(pageable);
                } else {
                        String companyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
                        notifications = notificationService.getNotificationsByCompany(companyId, pageable);
                }

                return ResponseEntity.ok(
                                ApiResponse.<Page<NotificationResponseDto>>builder()
                                                .success(true)
                                                .responseMessage("Notifications retrieved successfully")
                                                .data(notifications)
                                                .build());
        }

        @GetMapping("/company/{companyId}")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<List<NotificationResponseDto>>> getNotificationsByCompany(
                        @PathVariable String companyId) {
                String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
                log.info("Fetching notifications for company: {}", resolvedCompanyId);

                List<NotificationResponseDto> notifications = notificationService.getNotificationsByCompany(resolvedCompanyId);

                return ResponseEntity.ok(
                                ApiResponse.<List<NotificationResponseDto>>builder()
                                                .success(true)
                                                .responseMessage("Notifications retrieved successfully")
                                                .data(notifications)
                                                .build());
        }

        @GetMapping("/company/{companyId}/unread-count")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Long>> getUnreadCount(@PathVariable String companyId) {
                String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
                log.info("Fetching unread count for company: {}", resolvedCompanyId);

                long count = notificationService.getUnreadCount(resolvedCompanyId);

                return ResponseEntity.ok(
                                ApiResponse.<Long>builder()
                                                .success(true)
                                                .responseMessage("Unread count retrieved successfully")
                                                .data(count)
                                                .build());
        }

        @PutMapping("/{id}/read")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<NotificationResponseDto>> markAsRead(@PathVariable Long id) {
                log.info("Marking notification as read: {}", id);

                NotificationResponseDto notification = notificationService.markAsRead(id);

                return ResponseEntity.ok(
                                ApiResponse.<NotificationResponseDto>builder()
                                                .success(true)
                                                .responseMessage("Notification marked as read")
                                                .data(notification)
                                                .build());
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable Long id) {
                log.info("Deleting notification: {}", id);

                notificationService.deleteNotification(id);

                return ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .responseMessage("Notification deleted successfully")
                                .build());
        }
}
