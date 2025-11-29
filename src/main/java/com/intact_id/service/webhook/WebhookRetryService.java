package com.intact_id.service.webhook;

import com.intact_id.config.WebhookConfig;
import com.intact_id.db.model.WebhookDelivery;
import com.intact_id.db.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for retrying failed webhook deliveries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRetryService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookService webhookService;
    private final WebhookConfig webhookConfig;

    /**
     * Process failed webhook deliveries that are eligible for retry
     * Runs every minute
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void processRetries() {
        if (!webhookConfig.isEnabled()) {
            return;
        }

        try {
            List<WebhookDelivery> eligibleDeliveries = webhookDeliveryRepository.findEligibleForRetry(
                    LocalDateTime.now(),
                    webhookConfig.getMaxRetries());

            if (eligibleDeliveries.isEmpty()) {
                return;
            }

            log.info("Found {} webhook deliveries eligible for retry", eligibleDeliveries.size());

            for (WebhookDelivery delivery : eligibleDeliveries) {
                try {
                    webhookService.retryDelivery(delivery);
                } catch (Exception e) {
                    log.error("Failed to retry webhook delivery: {}", delivery.getId(), e);
                }
            }

        } catch (Exception e) {
            log.error("Error processing webhook retries", e);
        }
    }
}
