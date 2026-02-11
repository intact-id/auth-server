package com.intact_id.db.repository;

import com.intact_id.db.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
