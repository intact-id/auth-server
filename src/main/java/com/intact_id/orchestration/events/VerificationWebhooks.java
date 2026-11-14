package com.intact_id.orchestration.events;

import com.intact_id.notifications.WebhookService;
import com.intact_id.orchestration.PartyInviteCreated;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.kyb.KybParties;
import com.intact_id.orchestration.kyb.VerificationParty;
import com.intact_id.orchestration.model.VerificationStep;
import com.intact_id.orchestration.model.VerificationStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the webhook payloads merchants receive (payload version 2); notifications wraps them in the envelope
 * {id, type, version, createdAt, data} and delivers them. Events:
 * <ul>
 *   <li>verification.completed: approved or rejected (a reviewer's decision included)</li>
 *   <li>verification.failed: could not be finished on our side</li>
 *   <li>verification.review_required: a person must decide</li>
 *   <li>business.waiting_for_people: a business waits for its directors and owners</li>
 *   <li>party.invite_created: a director or owner got a new verification link</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class VerificationWebhooks {

    public static final String COMPLETED = "verification.completed";
    public static final String FAILED = "verification.failed";
    public static final String REVIEW_REQUIRED = "verification.review_required";
    public static final String WAITING_FOR_PEOPLE = "business.waiting_for_people";
    public static final String INVITE_CREATED = "party.invite_created";

    /** Steps the processor records, as the catalog codes merchants build workflows from */
    private static final Map<String, String> SERVICE_OF_STEP = Map.of(
            "ocr_extraction", "DOCUMENT_CHECK",
            "face_match", "FACE_MATCH",
            "liveness_detection", "LIVENESS",
            "sanctions_screening", "SANCTIONS_SCREENING",
            "risk_assessment", "CRA",
            "business_registry", "BUSINESS_REGISTRY",
            "owner_verification", "OWNER_VERIFICATION");

    private final WebhookService webhookService;
    private final VerificationStepRepository steps;
    private final KybParties kybParties;

    public void completed(VerificationRequest request) {
        publish(request, COMPLETED, verification(request));
    }

    public void failed(VerificationRequest request) {
        publish(request, FAILED, verification(request));
    }

    public void reviewRequired(VerificationRequest request) {
        Map<String, Object> data = verification(request);
        data.put("reviewReason", request.getReviewReason());
        publish(request, REVIEW_REQUIRED, data);
    }

    public void waitingForPeople(VerificationRequest request) {
        publish(request, WAITING_FOR_PEOPLE, verification(request));
    }

    public void inviteCreated(PartyInviteCreated invite, String companyName) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verificationId", invite.businessVerificationId());
        data.put("externalReference", invite.businessReference());
        data.put("partyId", invite.partyId());
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("firstName", invite.firstName());
        person.put("lastName", invite.lastName());
        person.put("email", invite.email());
        data.put("applicant", person);
        data.put("link", invite.link());
        data.put("expiresAt", invite.expiresAt());
        // false: Intact didn't email it, so share it with the person yourself
        data.put("emailed", invite.emailed());
        data.put("reminder", invite.reminder());
        webhookService.publish(invite.companyId(), companyName, invite.businessVerificationId(), INVITE_CREATED, data);
    }

    private void publish(VerificationRequest request, String type, Map<String, Object> data) {
        webhookService.publish(request.getCompany().getId(), request.getCompany().getLegalName(), request.getId(), type, data);
    }

    /** The verification as merchants see it: what was verified, the decision, and each check that ran. */
    private Map<String, Object> verification(VerificationRequest request) {
        boolean business = "business".equalsIgnoreCase(request.getVerificationType());
        Map<String, Object> customer = Optional.ofNullable(request.getCustomerData()).orElse(Map.of());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("verificationId", request.getId());
        data.put("externalReference", request.getExternalReference());
        data.put("type", business ? "business" : "individual");
        data.put("status", request.getStatus().name());
        data.put("decision", decision(request));
        data.put("riskScore", request.getRiskScore());
        if (request.getFailureReason() != null || request.getErrorDetails() != null) {
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("code", request.getFailureReason());
            reason.put("message", request.getErrorDetails());
            data.put("reason", reason);
        }
        if (request.getWorkflowId() != null) {
            Map<String, Object> workflow = new LinkedHashMap<>();
            workflow.put("id", request.getWorkflowId());
            workflow.put("version", request.getWorkflowVersion());
            data.put("workflow", workflow);
        }
        data.put("tier", request.getTier());

        if (business) {
            data.put("business", customer);
            data.put("parties", kybParties.forVerification(request.getId()).stream().map(VerificationWebhooks::party).toList());
        } else {
            Map<String, Object> applicant = new LinkedHashMap<>();
            for (String field : List.of("firstName", "lastName", "dateOfBirth", "nationality", "countryOfResidence", "email", "phone")) {
                if (customer.get(field) != null) applicant.put(field, customer.get(field));
            }
            data.put("applicant", applicant);
            // A director or owner verifying for a business: where they belong
            KybParties.partyIdFromReference(request.getExternalReference()).ifPresent(id -> data.put("partyId", id));
        }

        data.put("checks", steps.findByVerificationRequestIdOrderByCreatedAtAsc(request.getId()).stream()
                .map(VerificationWebhooks::check).toList());
        data.put("submittedAt", request.getCreatedAt());
        data.put("completedAt", request.getCompletedAt());
        return data;
    }

    /** approved, rejected, manual_review, waiting or error */
    private static String decision(VerificationRequest request) {
        return switch (request.getStatus()) {
            case COMPLETED -> Optional.ofNullable(request.getOverallDecision()).orElse("approved");
            case REJECTED -> "rejected";
            case FAILED -> "error";
            case PENDING_APPROVAL -> "manual_review";
            case PENDING -> "waiting";
            default -> Optional.ofNullable(request.getOverallDecision()).orElse(request.getStatus().name().toLowerCase());
        };
    }

    private static Map<String, Object> check(VerificationStep step) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("service", SERVICE_OF_STEP.getOrDefault(step.getStepName(), step.getStepName().toUpperCase()));
        check.put("status", step.getCheckStatus() != null ? step.getCheckStatus() : step.getStatus().name().toLowerCase());
        check.put("score", step.getConfidenceScore());
        Object reason = step.getResult() == null ? null : step.getResult().get("reason");
        if (reason != null) check.put("reason", reason.toString());
        check.put("completedAt", step.getExecutedAt());
        return check;
    }

    private static Map<String, Object> party(VerificationParty p) {
        Map<String, Object> party = new LinkedHashMap<>();
        party.put("partyId", p.getId());
        party.put("firstName", p.getFirstName());
        party.put("lastName", p.getLastName());
        party.put("director", p.isDirector());
        party.put("ownershipPercent", p.getOwnershipPercent());
        party.put("required", p.isMustVerify());
        party.put("status", p.getStatus().name());
        party.put("verificationId", p.getPersonVerificationId());
        return party;
    }
}
