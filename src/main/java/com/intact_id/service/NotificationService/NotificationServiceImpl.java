package com.intact_id.service.NotificationService;

import com.intact_id.db.model.Company;
import com.intact_id.db.model.Notification;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.db.repository.NotificationRepository;
import com.intact_id.dto.request.NotificationRequestDto;
import com.intact_id.dto.response.NotificationResponseDto;
import com.intact_id.util.TenantContext;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final CompanyRepository companyRepository;
    private final com.intact_id.service.email.EmailService emailService;

    @Override
    @Transactional
    public NotificationResponseDto createNotification(NotificationRequestDto request) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(request.getCompanyId());
        request.setCompanyId(resolvedCompanyId);
        log.info("Creating notification for company: {}", resolvedCompanyId);

        Company company = companyRepository.findById(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found with id: " + resolvedCompanyId));

        Notification notification = new Notification();
        notification.setCompany(company);
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setType(Notification.NotificationType.valueOf(request.getType().toUpperCase()));
        notification.setStatus(Notification.NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created with id: {}", saved.getId());

        // Send email notification
        try {
            String emailBody = buildEmailBody(saved);
            String recipientEmail = company.getCommunication() != null && company.getCommunication().getEmail() != null
                    ? company.getCommunication().getEmail()
                    : "no-reply@intact-id.com"; // Fallback email

            emailService.sendSimpleEmail(
                    recipientEmail,
                    saved.getTitle(),
                    emailBody);
            log.info("Email notification sent to: {}", recipientEmail);
        } catch (Exception e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            // Don't fail the notification creation if email fails
        }

        return mapToDto(saved);
    }

    @Override
    @Transactional
    public NotificationResponseDto persistNotification(String companyId, String title, String message, String type) {
        log.info("Persisting notification for company: {}", companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found with id: " + companyId));

        Notification notification = new Notification();
        notification.setCompany(company);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(Notification.NotificationType.valueOf(type.toUpperCase()));
        notification.setStatus(Notification.NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);
        log.info("Notification persisted with id: {}", saved.getId());

        return mapToDto(saved);
    }

    @Override
    public Page<NotificationResponseDto> getAllNotifications(Pageable pageable) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(null);
        if (resolvedCompanyId != null) {
            log.info("Fetching notifications for company: {} with pagination", resolvedCompanyId);
            return notificationRepository.findByCompanyIdOrderByCreatedAtDesc(resolvedCompanyId, pageable)
                    .map(this::mapToDto);
        }

        log.info("Fetching all notifications with pagination");
        return notificationRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::mapToDto);
    }

    @Override
    public List<NotificationResponseDto> getNotificationsByCompany(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Fetching notifications for company: {}", resolvedCompanyId);
        return notificationRepository.findByCompanyIdOrderByCreatedAtDesc(resolvedCompanyId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public Page<NotificationResponseDto> getNotificationsByCompany(String companyId, Pageable pageable) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Fetching notifications for company: {} with pagination", resolvedCompanyId);
        return notificationRepository.findByCompanyIdOrderByCreatedAtDesc(resolvedCompanyId, pageable)
                .map(this::mapToDto);
    }

    @Override
    @Transactional
    public NotificationResponseDto markAsRead(Long notificationId) {
        log.info("Marking notification as read: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Notification not found with id: " + notificationId));

        enforceNotificationScope(notification);

        notification.setStatus(Notification.NotificationStatus.READ);
        notification.setReadAt(LocalDateTime.now());

        Notification updated = notificationRepository.save(notification);
        log.info("Notification marked as read: {}", notificationId);

        return mapToDto(updated);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        log.info("Deleting notification: {}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException(
                        "Notification not found with id: " + notificationId));

        enforceNotificationScope(notification);
        notificationRepository.delete(notification);
        log.info("Notification deleted: {}", notificationId);
    }

    @Override
    public long getUnreadCount(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        return notificationRepository.countByCompanyIdAndStatus(
                resolvedCompanyId,
                Notification.NotificationStatus.UNREAD);
    }

    private NotificationResponseDto mapToDto(Notification notification) {
        return NotificationResponseDto.builder()
                .id(notification.getId())
                .companyId(notification.getCompany().getId())
                .companyName(notification.getCompany().getLegalName())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType().name())
                .status(notification.getStatus().name())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    private void enforceNotificationScope(Notification notification) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(null);
        if (resolvedCompanyId != null) {
            String notificationCompanyId = notification.getCompany() != null ? notification.getCompany().getId() : null;
            if (notificationCompanyId == null || !notificationCompanyId.equals(resolvedCompanyId)) {
                throw new ApplicationExceptions.IntactUnauthorizedException("Access denied");
            }
        }
    }

    private String buildEmailBody(Notification notification) {
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(notification.getCompany().getLegalName()).append(",\n\n");
        body.append(notification.getMessage()).append("\n\n");
        body.append("Notification Type: ").append(notification.getType()).append("\n");
        body.append("Date: ").append(notification.getCreatedAt()).append("\n\n");
        body.append("Best regards,\n");
        body.append("Intact ID Team");
        return body.toString();
    }
}
