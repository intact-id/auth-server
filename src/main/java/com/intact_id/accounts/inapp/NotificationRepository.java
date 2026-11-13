package com.intact_id.accounts.inapp;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCompanyIdOrderByCreatedAtDesc(String companyId);

    Page<Notification> findByCompanyIdOrderByCreatedAtDesc(String companyId, Pageable pageable);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Notification> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            String companyId,
            Notification.NotificationStatus status);

    long countByCompanyIdAndStatus(String companyId, Notification.NotificationStatus status);

    // Intact staff's own inbox
    Page<Notification> findByCompanyIsNullOrderByCreatedAtDesc(Pageable pageable);

    long countByCompanyIsNullAndStatus(Notification.NotificationStatus status);

    // What staff sent to companies
    Page<Notification> findByCompanyIsNotNullOrderByCreatedAtDesc(Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :read, n.readAt = :now WHERE n.company.id = :companyId AND n.status = :unread")
    int markAllRead(@Param("companyId") String companyId, @Param("now") LocalDateTime now,
                    @Param("read") Notification.NotificationStatus read, @Param("unread") Notification.NotificationStatus unread);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :read, n.readAt = :now WHERE n.company IS NULL AND n.status = :unread")
    int markAllStaffRead(@Param("now") LocalDateTime now,
                         @Param("read") Notification.NotificationStatus read, @Param("unread") Notification.NotificationStatus unread);
}
