package com.intact_id.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.config.WebhookConfig;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.WebhookDelivery;
import com.intact_id.db.model.kyc.VerificationRequest;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.WebhookDeliveryRepository;
import com.intact_id.dto.webhook.WebhookEventDto;
import com.intact_id.service.webhook.events.WebhookEventData;
import com.intact_id.service.webhook.events.WebhookNotificationEvent;
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
    private final ApplicationEventPublisher eventPublisher;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Publish event for verification completed webhook
     */
    public void sendVerificationCompletedWebhook(Company company, VerificationRequest verificationRequest) {
        if (!webhookConfig.isEnabled()) {
            log.debug("Webhooks are disabled, skipping notification");
            return;
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("verification_id", verificationRequest.getId());
        eventData.put("status", verificationRequest.getStatus().name());
        eventData.put("tier", verificationRequest.getTier());
        eventData.put("verification_type", verificationRequest.getVerificationType());
        eventData.put("overall_decision", verificationRequest.getOverallDecision());
        eventData.put("risk_score", verificationRequest.getRiskScore());
        eventData.put("completed_at", verificationRequest.getCompletedAt());

        WebhookEventData data = new WebhookEventData(
                company.getId(),
                verificationRequest.getId(),
                "verification.completed",
                eventData);

        eventPublisher.publishEvent(new WebhookNotificationEvent(this, data));
    }

    /**
     * Publish event for verification failed webhook
     */
    public void sendVerificationFailedWebhook(Company company, VerificationRequest verificationRequest) {
        if (!webhookConfig.isEnabled()) {
            log.debug("Webhooks are disabled, skipping notification");
            return;
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("verification_id", verificationRequest.getId());
        eventData.put("status", verificationRequest.getStatus().name());
        eventData.put("tier", verificationRequest.getTier());
        eventData.put("verification_type", verificationRequest.getVerificationType());
        eventData.put("failed_at", LocalDateTime.now());

        WebhookEventData data = new WebhookEventData(
                company.getId(),
                verificationRequest.getId(),
                "verification.failed",
                eventData);

        eventPublisher.publishEvent(new WebhookNotificationEvent(this, data));
    }

    /**
     * Send webhook notification (called by event handler)
     */
    public void sendWebhookNotification(Company company, WebhookEventDto event) {
        try {
            // Get webhook configuration from company communication details
            CompanyCommunicationDetails communication = communicationRepository
                    .findByCompanyId(company.getId())
                    .orElse(null);

            if (communication == null || !communication.isWebhookEnabled()) {
                log.debug("Webhooks not enabled for company: {}", company.getId());
                return;
            }

            String webhookUrl = communication.getWebhookUrl();
            log.info("Sending webhook to: {} for event: {}", webhookUrl, event.getEventType());

            // Build payload
            String payload = objectMapper.writeValueAsString(event);

            // Generate signature using company's API key as secret
            String signature = generateWebhookSignature(payload, company.getId());

            // Create delivery record
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setCompany(company);
            delivery.setVerificationId(event.getVerificationId());
            delivery.setWebhookUrl(webhookUrl);
            delivery.setEventType(event.getEventType());
            delivery.setPayload(payload);
            delivery.setStatus(WebhookDelivery.DeliveryStatus.PENDING);

            delivery = webhookDeliveryRepository.save(delivery);

            // Send HTTP request
            sendHttpRequest(delivery, webhookUrl, payload, signature, communication.getWebhookTimeoutSeconds());

        } catch (Exception e) {
            log.error("Failed to send webhook notification for company: {}", company.getId(), e);
        }
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
     * Generate HMAC-SHA256 signature for webhook payload
     */
    private String generateWebhookSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(webhookConfig.getSignatureAlgorithm());
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    webhookConfig.getSignatureAlgorithm());
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (Exception e) {
            log.error("Failed to generate webhook signature", e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    /**
     * Retry failed webhook delivery
     */
    @Transactional
    public void retryDelivery(WebhookDelivery delivery) {
        try {
            log.info("Retrying webhook delivery: {} (attempt {})", delivery.getId(), delivery.getRetryCount() + 1);

            CompanyCommunicationDetails communication = communicationRepository
                    .findByCompanyId(delivery.getCompany().getId())
                    .orElseThrow();

            String signature = generateWebhookSignature(delivery.getPayload(), delivery.getCompany().getId());

            sendHttpRequest(delivery, delivery.getWebhookUrl(), delivery.getPayload(),
                    signature, communication.getWebhookTimeoutSeconds());

        } catch (Exception e) {
            log.error("Retry failed for webhook delivery: {}", delivery.getId(), e);
            delivery.markAsFailed(0, e.getMessage());
            scheduleRetry(delivery);
            webhookDeliveryRepository.save(delivery);
        }
    }
}
