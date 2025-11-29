package com.intact_id.db.repository;

import com.intact_id.db.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for webhook delivery tracking
 */
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, String> {

    /**
     * Find all pending deliveries
     */
    List<WebhookDelivery> findByStatus(WebhookDelivery.DeliveryStatus status);

    /**
     * Find failed deliveries eligible for retry
     */
    @Query("SELECT w FROM WebhookDelivery w WHERE w.status IN ('FAILED', 'RETRYING') " +
            "AND w.nextRetryAt <= :now AND w.retryCount < :maxRetries")
    List<WebhookDelivery> findEligibleForRetry(
            @Param("now") LocalDateTime now,
            @Param("maxRetries") int maxRetries);

    /**
     * Find deliveries by company
     */
    Page<WebhookDelivery> findByCompanyId(String companyId, Pageable pageable);

    /**
     * Find deliveries by verification ID
     */
    List<WebhookDelivery> findByVerificationId(String verificationId);

    /**
     * Count delivery attempts for a verification
     */
    long countByVerificationId(String verificationId);
}
