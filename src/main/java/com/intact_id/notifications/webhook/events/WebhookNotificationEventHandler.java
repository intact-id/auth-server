package com.intact_id.notifications.webhook.events;

import com.intact_id.config.events.InternalEventHandler;
import com.intact_id.notifications.WebhookEventDto;
import com.intact_id.notifications.WebhookService;
import com.intact_id.util.exception.ApplicationExceptions;
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

    // Dispatched exclusively via InternalApplicationEventListener, which routes InternalEvent
    // subtypes to the matching InternalEventHandler bean. Do NOT add @EventListener here —
    // this method would then be invoked twice per event (once directly by Spring, once via
    // that generic dispatch), which is exactly the bug this comment replaced: every webhook
    // firing — and being recorded as a delivery — twice.
    @Override
    public void handle(WebhookNotificationEvent event) {
        WebhookEventData data = event.getData();

        log.info("Processing webhook notification event: {} for company: {}",
                data.getEventType(), data.getCompanyId());

        try {
            // Build webhook event DTO
            WebhookEventDto webhookEvent = WebhookEventDto.builder()
                    .eventType(data.getEventType())
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(LocalDateTime.now())
                    .companyId(data.getCompanyId())
                    .companyName(data.getCompanyName())
                    .verificationId(data.getVerificationId())
                    .data(convertToMap(data.getEventData()))
                    .build();

            // Send webhook (synchronously in async context)
            webhookService.sendWebhookNotification(data.getCompanyId(), webhookEvent);

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
