package com.intact_id.accounts;

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

    /** For Intact staff (super admins), shown in their bell. Never fails the caller. link: console page, or null. */
    void notifyStaff(String title, String message, String type, String link);

    /** The signed-in person's inbox: their company's notifications, or the staff inbox for super admins. */
    Page<NotificationResponseDto> inbox(Pageable pageable);

    long inboxUnreadCount();

    int markInboxRead();
}
