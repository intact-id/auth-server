package com.intact_id.service.NotificationService;

import com.intact_id.dto.request.NotificationRequestDto;
import com.intact_id.dto.response.NotificationResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {

    NotificationResponseDto createNotification(NotificationRequestDto request);

    /**
     * Persist a notification without sending email (for system-generated
     * notifications)
     */
    NotificationResponseDto persistNotification(String companyId, String title, String message, String type);

    Page<NotificationResponseDto> getAllNotifications(Pageable pageable);

    List<NotificationResponseDto> getNotificationsByCompany(String companyId);

    Page<NotificationResponseDto> getNotificationsByCompany(String companyId, Pageable pageable);

    NotificationResponseDto markAsRead(Long notificationId);

    void deleteNotification(Long notificationId);

    long getUnreadCount(String companyId);
}
