package com.intact_id.service.webhook.events;

import com.intact_id.config.events.InternalEventHandler;
import com.intact_id.db.model.Company;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.dto.webhook.WebhookEventDto;
import com.intact_id.service.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handler for webhook notification events
 * Processes webhook sending asynchronously with security context
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookNotificationEventHandler implements InternalEventHandler<WebhookNotificationEvent> {

    private final WebhookService webhookService;
    private final CompanyRepository companyRepository;

    @Override
    public void handle(WebhookNotificationEvent event) {
        WebhookEventData data = event.getData();

        log.info("Processing webhook notification event: {} for company: {}",
                data.getEventType(), data.getCompanyId());

        try {
            // Fetch company
            Company company = companyRepository.findById(data.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Company not found: " + data.getCompanyId()));

            // Build webhook event DTO
            WebhookEventDto webhookEvent = WebhookEventDto.builder()
                    .eventType(data.getEventType())
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .companyId(data.getCompanyId())
                    .verificationId(data.getVerificationId())
                    .data(convertToMap(data.getEventData()))
                    .build();

            // Send webhook (synchronously in async context)
            webhookService.sendWebhookNotification(company, webhookEvent);

        } catch (Exception e) {
            log.error("Failed to process webhook notification event for company: {}",
                    data.getCompanyId(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object eventData) {
        if (eventData instanceof Map) {
            return (Map<String, Object>) eventData;
        }
        return new HashMap<>();
    }
}
