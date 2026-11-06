package com.intact_id.orchestration.events;

import com.intact_id.notifications.WebhookService;
import com.intact_id.orchestration.VerificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Builds the verification webhook payloads merchants receive; notifications delivers them. */
@Component
@RequiredArgsConstructor
public class VerificationWebhooks {

    private final WebhookService webhookService;

    /**
     * Publish event for verification completed webhook
     */
    public void completed(VerificationRequest verificationRequest) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("verification_id", verificationRequest.getId());
        if (verificationRequest.getExternalReference() != null) {
            eventData.put("external_reference", verificationRequest.getExternalReference());
        }
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

        webhookService.publish(verificationRequest.getCompany().getId(), verificationRequest.getId(),
                "verification.completed", eventData);
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
    public void failed(VerificationRequest verificationRequest) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("verification_id", verificationRequest.getId());
        if (verificationRequest.getExternalReference() != null) {
            eventData.put("external_reference", verificationRequest.getExternalReference());
        }
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

        webhookService.publish(verificationRequest.getCompany().getId(), verificationRequest.getId(),
                "verification.failed", eventData);
    }
}
