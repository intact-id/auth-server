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
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
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
    private final GeneratorUtils generatorUtils;
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
        eventData.put("overall_decision", verificationRequest.getOverallDecision());
        eventData.put("tier", verificationRequest.getTier());
        eventData.put("verification_type", verificationRequest.getVerificationType());
        eventData.put("completed_at", verificationRequest.getCompletedAt());
        eventData.put("started_at", verificationRequest.getCreatedAt());

        // Applicant information
        Map<String, Object> applicant = new HashMap<>();
        Map<String, Object> customerData = verificationRequest.getCustomerData();
        if (customerData != null) {
            if (customerData.containsKey("email")) {
                applicant.put("email", customerData.get("email"));
            }
            if (customerData.containsKey("phone")) {
                applicant.put("phone", customerData.get("phone"));
            }
            if (customerData.containsKey("firstName") || customerData.containsKey("lastName")) {
                String name = "";
                if (customerData.containsKey("firstName")) {
                    name += customerData.get("firstName");
                }
                if (customerData.containsKey("lastName")) {
                    name += (name.isEmpty() ? "" : " ") + customerData.get("lastName");
                }
                applicant.put("name", name);
            }

            // Add metadata if present
            if (verificationRequest.getMetadata() != null && !verificationRequest.getMetadata().isEmpty()) {
                applicant.put("metadata", verificationRequest.getMetadata());
            }
        }
        eventData.put("applicant", applicant);

        // Top-level rejection reason (present only when verification was rejected/failed)
        if (verificationRequest.getFailureReason() != null) {
            eventData.put("rejection_reason", verificationRequest.getFailureReason());
            eventData.put("rejection_details", verificationRequest.getErrorDetails() != null
                    ? verificationRequest.getErrorDetails()
                    : getUserFriendlyErrorDetails(verificationRequest.getFailureReason()));
        }

        // Verification results
        Map<String, Object> results = new HashMap<>();

        // Face screening results
        Map<String, Object> faceScreening = new HashMap<>();

        // Determine status based on overall decision and failure reason
        String faceStatus = "completed";
        String faceDecision = verificationRequest.getOverallDecision() != null
                ? verificationRequest.getOverallDecision()
                : "pending";

        if (verificationRequest.getFailureReason() != null) {
            faceStatus = "error";
            faceDecision = "failed";

            // Structured error information
            Map<String, Object> error = new HashMap<>();
            error.put("code", verificationRequest.getFailureReason());
            error.put("message", verificationRequest.getErrorDetails() != null ? verificationRequest.getErrorDetails()
                    : "Verification failed");

            // Add user-friendly details based on error code
            String details = getUserFriendlyErrorDetails(verificationRequest.getFailureReason());
            error.put("details", details);

            faceScreening.put("error", error);
        }

        faceScreening.put("status", faceStatus);
        faceScreening.put("decision", faceDecision);

        // Add check-level details
        Map<String, Object> checks = new HashMap<>();
        checks.put("face_detected", !"FaceNotDetectedError".equals(verificationRequest.getFailureReason()));
        checks.put("liveness_passed", verificationRequest.getFailureReason() == null ? true : null);
        checks.put("face_match_score",
                verificationRequest.getRiskScore() != null ? (1.0 - verificationRequest.getRiskScore()) : null);
        checks.put("quality_score", verificationRequest.getFailureReason() == null ? 0.95 : null);

        faceScreening.put("checks", checks);
        results.put("face_screening", faceScreening);

        eventData.put("results", results);

        WebhookEventData data = new WebhookEventData(
                company.getId(),
                verificationRequest.getId(),
                "verification.completed",
                eventData);

        eventPublisher.publishEvent(new WebhookNotificationEvent(this, data));
    }

    /**
     * Get user-friendly error details based on error code
     */
    private String getUserFriendlyErrorDetails(String errorCode) {
        if (errorCode == null)
            return "An unknown error occurred";

        switch (errorCode) {
            case "FaceNotDetectedError":
                return "The selfie image does not contain a detectable face. Please ensure the photo is clear and the face is visible.";
            case "LivenessCheckFailed":
                return "The liveness check failed. Please ensure you are taking a live photo and not using a screenshot or printed image.";
            case "FaceMatchFailed":
                return "The face in the selfie does not match the face in the ID document. Please ensure you are using your own ID.";
            case "ImageQualityTooLow":
                return "The image quality is too low. Please ensure good lighting and a clear, focused image.";
            case "SystemError":
                return "A system error occurred during verification. Please try again later.";
            default:
                return "Verification failed. Please check your documents and try again.";
        }
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
        eventData.put("overall_decision", "error");
        eventData.put("tier", verificationRequest.getTier());
        eventData.put("verification_type", verificationRequest.getVerificationType());
        eventData.put("failed_at", LocalDateTime.now());
        eventData.put("started_at", verificationRequest.getCreatedAt());

        // Applicant information
        Map<String, Object> applicant = new HashMap<>();
        Map<String, Object> customerData = verificationRequest.getCustomerData();
        if (customerData != null) {
            if (customerData.containsKey("email")) {
                applicant.put("email", customerData.get("email"));
            }
            if (customerData.containsKey("phone")) {
                applicant.put("phone", customerData.get("phone"));
            }
            if (customerData.containsKey("firstName") || customerData.containsKey("lastName")) {
                String name = "";
                if (customerData.containsKey("firstName")) {
                    name += customerData.get("firstName");
                }
                if (customerData.containsKey("lastName")) {
                    name += (name.isEmpty() ? "" : " ") + customerData.get("lastName");
                }
                applicant.put("name", name);
            }
            if (verificationRequest.getMetadata() != null && !verificationRequest.getMetadata().isEmpty()) {
                applicant.put("metadata", verificationRequest.getMetadata());
            }
        }
        eventData.put("applicant", applicant);

        // Top-level rejection reason
        eventData.put("rejection_reason", verificationRequest.getFailureReason() != null
                ? verificationRequest.getFailureReason() : "SystemError");
        eventData.put("rejection_details", verificationRequest.getErrorDetails() != null
                ? verificationRequest.getErrorDetails()
                : getUserFriendlyErrorDetails(verificationRequest.getFailureReason()));

        // Verification results with error details
        Map<String, Object> results = new HashMap<>();
        Map<String, Object> faceScreening = new HashMap<>();

        faceScreening.put("status", "error");
        faceScreening.put("decision", "failed");

        // Structured error information
        Map<String, Object> error = new HashMap<>();
        error.put("code", verificationRequest.getFailureReason() != null ? verificationRequest.getFailureReason()
                : "SystemError");
        error.put("message", verificationRequest.getErrorDetails() != null ? verificationRequest.getErrorDetails()
                : "Verification failed");
        error.put("details", getUserFriendlyErrorDetails(verificationRequest.getFailureReason()));

        faceScreening.put("error", error);

        // Check-level details
        Map<String, Object> checks = new HashMap<>();
        checks.put("face_detected", false);
        checks.put("liveness_passed", null);
        checks.put("face_match_score", null);
        checks.put("quality_score", null);

        faceScreening.put("checks", checks);
        results.put("face_screening", faceScreening);

        eventData.put("results", results);

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

            // Build payload with field-level filtering applied
            String payload = objectMapper.writeValueAsString(buildFilteredPayload(event, communication));

            // Generate signature using the company's dedicated webhook secret
            String secret = communication.getWebhookSecret();
            if (secret == null || secret.isBlank()) {
                log.error("No webhook secret configured for company: {} — skipping delivery", company.getId());
                return;
            }
            String signature = generatorUtils.generateWebhookSignature(payload, secret);

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
                    .findByCompanyId(delivery.getCompany().getId())
                    .orElseThrow();

            String signature = generatorUtils.generateWebhookSignature(delivery.getPayload(), communication.getWebhookSecret());

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
            testEvent.put("company_id", communication.getCompany().getId());

            String payload = objectMapper.writeValueAsString(testEvent);

            // Generate signature using the company's dedicated webhook secret
            String signature = generatorUtils.generateWebhookSignature(payload, communication.getWebhookSecret());

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
