package com.intact_id.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.notifications.webhook.WebhookConfig;
import com.intact_id.notifications.webhook.WebhookDelivery;
import com.intact_id.notifications.webhook.WebhookDeliveryRepository;
import com.intact_id.notifications.webhook.events.WebhookEventData;
import com.intact_id.notifications.webhook.events.WebhookNotificationEvent;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.notifications.webhook.WebhookSigner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Service for sending webhook notifications
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final CompanyCommunicationRepository communicationRepository;
    private final WebhookConfig webhookConfig;
    private final ObjectMapper objectMapper;
    private final WebhookSigner webhookSigner;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Queues a webhook for the company; delivery, signing and retries run in the background.
     */
    public void publish(String companyId, String companyName, String referenceId, String eventType,
                        Map<String, Object> eventData) {
        if (!webhookConfig.isEnabled()) {
            log.debug("Webhooks are disabled, skipping notification");
            return;
        }
        eventPublisher.publishEvent(new WebhookNotificationEvent(this,
                new WebhookEventData(companyId, companyName, referenceId, eventType, eventData)));
    }

    /**
     * Send webhook notification (called by event handler)
     */
    public void sendWebhookNotification(String companyId, WebhookEventDto event) {
        try {
            // Get webhook configuration from company communication details
            CompanyCommunicationDetails communication = communicationRepository
                    .findByCompanyId(companyId)
                    .orElse(null);

            if (communication == null || !communication.isWebhookEnabled()) {
                log.debug("Webhooks not enabled for company: {}", companyId);
                return;
            }

            String webhookUrl = communication.getWebhookUrl();
            log.info("Sending webhook to: {} for event: {}", webhookUrl, event.getEventType());

            // Build payload with field-level filtering applied
            String payload = objectMapper.writeValueAsString(buildFilteredPayload(event, communication));

            // Generate signature using the company's dedicated webhook secret
            String secret = communication.getWebhookSecret();
            if (secret == null || secret.isBlank()) {
                log.error("No webhook secret configured for company: {} — skipping delivery", companyId);
                return;
            }
            String signature = webhookSigner.sign(payload, secret);

            // Create delivery record
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setCompanyId(companyId);
            delivery.setVerificationId(event.getVerificationId());
            delivery.setWebhookUrl(webhookUrl);
            delivery.setEventType(event.getEventType());
            delivery.setPayload(payload);
            delivery.setStatus(WebhookDelivery.DeliveryStatus.PENDING);

            delivery = webhookDeliveryRepository.save(delivery);

            // Send HTTP request
            sendHttpRequest(delivery, webhookUrl, payload, signature, communication.getWebhookTimeoutSeconds());

        } catch (Exception e) {
            log.error("Failed to send webhook notification for company: {}", companyId, e);
        }
    }

    /**
     * Build a filtered payload map based on the company's payloadFields configuration.
     * Sensitive fields (raw scores, applicant info, IDs) are stripped when disabled.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFilteredPayload(WebhookEventDto event, CompanyCommunicationDetails communication) {
        Map<String, Object> payload = objectMapper.convertValue(event, Map.class);

        if (!communication.isPayloadFieldEnabled("includeVerificationId")) {
            payload.remove("verificationId");
            Map<String, Object> d = (Map<String, Object>) payload.get("data");
            if (d != null) d.remove("verification_id");
        }
        if (!communication.isPayloadFieldEnabled("includeCompanyId")) {
            payload.remove("companyId");
        }
        if (!communication.isPayloadFieldEnabled("includeCompanyName")) {
            payload.remove("companyName");
        }

        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        if (data != null) {
            if (!communication.isPayloadFieldEnabled("includeRejectionReason")) {
                data.remove("rejection_reason");
                data.remove("rejection_details");
                Map<String, Object> results = (Map<String, Object>) data.get("results");
                if (results != null) {
                    results.forEach((key, val) -> {
                        if (val instanceof Map) ((Map<String, Object>) val).remove("error");
                    });
                }
            }
            if (!communication.isPayloadFieldEnabled("includeTier")) {
                data.remove("tier");
            }
            if (!communication.isPayloadFieldEnabled("includeTimestamps")) {
                data.remove("started_at");
                data.remove("completed_at");
                data.remove("failed_at");
            }
            if (!communication.isPayloadFieldEnabled("includeApplicant")) {
                data.remove("applicant");
            }
            if (!communication.isPayloadFieldEnabled("includeResults")) {
                data.remove("results");
            } else if (!communication.isPayloadFieldEnabled("includeRawScores")) {
                Map<String, Object> results = (Map<String, Object>) data.get("results");
                if (results != null) {
                    results.forEach((key, val) -> {
                        if (val instanceof Map) {
                            ((Map<String, Object>) val).remove("checks");
                        }
                    });
                }
            }
        }

        return payload;
    }

    /**
     * Send HTTP POST request to webhook URL
     */
    private void sendHttpRequest(WebhookDelivery delivery, String webhookUrl, String payload,
            String signature, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Event-Type", delivery.getEventType())
                    .header("User-Agent", "IntactID-Webhook/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            handleWebhookResponse(delivery, response);

        } catch (Exception e) {
            log.error("HTTP request failed for webhook delivery: {}", delivery.getId(), e);
            delivery.markAsFailed(0, e.getMessage());
            scheduleRetry(delivery);
            webhookDeliveryRepository.save(delivery);
        }
    }

    /**
     * Handle webhook HTTP response
     */
    private void handleWebhookResponse(WebhookDelivery delivery, HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String responseBody = response.body();

        if (statusCode >= 200 && statusCode < 300) {
            // Success
            log.info("Webhook delivered successfully: {} - Status: {}", delivery.getId(), statusCode);
            delivery.markAsDelivered(statusCode, responseBody);
        } else {
            // Failure
            log.warn("Webhook delivery failed: {} - Status: {}", delivery.getId(), statusCode);
            delivery.markAsFailed(statusCode, responseBody);
            scheduleRetry(delivery);
        }

        webhookDeliveryRepository.save(delivery);
    }

    /**
     * Schedule retry for failed delivery
     */
    private void scheduleRetry(WebhookDelivery delivery) {
        if (delivery.getRetryCount() >= webhookConfig.getMaxRetries()) {
            log.warn("Max retries reached for webhook delivery: {}", delivery.getId());
            delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            return;
        }
        int retryInterval = webhookConfig.getRetryInterval(delivery.getRetryCount());
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(retryInterval);

        delivery.incrementRetry();
        delivery.setNextRetryAt(nextRetry);

        log.info("Scheduled retry {} for webhook delivery: {} at {}",
                delivery.getRetryCount(), delivery.getId(), nextRetry);
    }


    /**
     * Retry failed webhook delivery
     */
    @Transactional
    public void retryDelivery(WebhookDelivery delivery) {
        try {
            log.info("Retrying webhook delivery: {} (attempt {})", delivery.getId(), delivery.getRetryCount() + 1);

            CompanyCommunicationDetails communication = communicationRepository
                    .findByCompanyId(delivery.getCompanyId())
                    .orElseThrow();

            String signature = webhookSigner.sign(delivery.getPayload(), communication.getWebhookSecret());

            sendHttpRequest(delivery, delivery.getWebhookUrl(), delivery.getPayload(),
                    signature, communication.getWebhookTimeoutSeconds());

        } catch (Exception e) {
            log.error("Retry failed for webhook delivery: {}", delivery.getId(), e);
            delivery.markAsFailed(0, e.getMessage());
            scheduleRetry(delivery);
            webhookDeliveryRepository.save(delivery);
        }
    }

    /**
     * Send test webhook to verify endpoint configuration
     */
    public Map<String, Object> sendTestWebhookDetailed(CompanyCommunicationDetails communication) {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("Sending test webhook to: {}", communication.getWebhookUrl());

            // Build test event payload
            Map<String, Object> testEvent = new HashMap<>();
            testEvent.put("event_type", "test.webhook");
            testEvent.put("event_id", java.util.UUID.randomUUID().toString());
            testEvent.put("timestamp", LocalDateTime.now().toString());
            testEvent.put("message", "This is a test webhook from IntactID");
            testEvent.put("company_id", communication.getCompanyId());

            String payload = objectMapper.writeValueAsString(testEvent);

            // Generate signature using the company's dedicated webhook secret
            String signature = webhookSigner.sign(payload, communication.getWebhookSecret());

            // Send HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(communication.getWebhookUrl()))
                    .timeout(Duration.ofSeconds(communication.getWebhookTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("X-Webhook-Signature", "sha256=" + signature)
                    .header("X-Event-Type", "test.webhook")
                    .header("User-Agent", "IntactID-Webhook/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            log.info("Test webhook response: Status={}, Body={}", statusCode, responseBody);

            result.put("success", statusCode >= 200 && statusCode < 300);
            result.put("status_code", statusCode);
            result.put("response_body", responseBody);
            result.put("webhook_url", communication.getWebhookUrl());

            return result;

        } catch (Exception e) {
            log.error("Failed to send test webhook", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("webhook_url", communication.getWebhookUrl());
            return result;
        }
    }

    public boolean sendTestWebhook(CompanyCommunicationDetails communication) {
        Map<String, Object> result = sendTestWebhookDetailed(communication);
        return (boolean) result.getOrDefault("success", false);
    }
}
