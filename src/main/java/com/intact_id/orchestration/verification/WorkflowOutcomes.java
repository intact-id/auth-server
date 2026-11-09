package com.intact_id.orchestration.verification;

import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.workflow.ServiceOutcome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the checks a run executed into catalog service outcomes and facts, using the output keys the catalog
 * declares (face.match_score, liveness.score, document.type, crosscheck.name_match, ...).
 */
final class WorkflowOutcomes {

    private WorkflowOutcomes() {
    }

    static Map<String, ServiceOutcome> from(ExternalKycServiceClient.VerificationResult result, Set<String> services) {
        Map<String, ServiceOutcome> outcomes = new HashMap<>();
        for (ExternalKycServiceClient.CheckResult check : result.getChecks()) {
            Map<String, Object> r = Optional.ofNullable(check.getResult()).orElse(Map.of());
            switch (check.getStepName()) {
                case "face_match" -> {
                    Map<String, Object> facts = new HashMap<>();
                    putIfPresent(facts, "face.match_score", r.get("match_score"));
                    putIfPresent(facts, "face.matched", r.get("verified"));
                    outcomes.put("FACE_MATCH", outcome(check.getStatus(), "Face did not match the ID photo", facts));
                }
                case "liveness_detection" -> {
                    Map<String, Object> facts = new HashMap<>();
                    putIfPresent(facts, "liveness.passed", r.get("passed"));
                    if (r.get("liveness_score") instanceof Number score) facts.put("liveness.score", score.doubleValue() / 100.0);
                    outcomes.put("LIVENESS", "skipped".equals(check.getStatus())
                            ? ServiceOutcome.review("Liveness service unavailable", facts)
                            : outcome(check.getStatus(), "Liveness check failed", facts));
                }
                case "ocr_extraction" -> {
                    Map<String, Object> document = new HashMap<>();
                    putIfPresent(document, "document.type", r.get("document_type"));
                    putIfPresent(document, "document.ocr_confidence", r.get("overall_confidence"));
                    Map<String, Object> extracted = Optional.ofNullable(result.getExtractedOcrData()).orElse(Map.of());
                    putIfPresent(document, "document.country", extracted.get("country_iso3"));
                    boolean unreadable = "review".equals(check.getStatus()) && r.get("document_type") == null
                            || "low_ocr_confidence".equals(r.get("reason"));
                    ServiceOutcome documentCheck = unreadable
                            ? ServiceOutcome.review("The document could not be read reliably", document)
                            : Boolean.TRUE.equals(r.get("document_expired"))
                            ? ServiceOutcome.failed("Document expired", document)
                            : ServiceOutcome.passed(document);
                    outcomes.put("DOCUMENT_CHECK", documentCheck);

                    Map<String, Object> crosscheck = new HashMap<>();
                    putBoolean(crosscheck, "crosscheck.name_match", r.get("name_match"));
                    putBoolean(crosscheck, "crosscheck.id_number_match", r.get("id_number_match"));
                    putBoolean(crosscheck, "crosscheck.dob_match", r.get("dob_match"));
                    String mismatch = r.get("failure_reasons") instanceof List<?> reasons && !reasons.isEmpty()
                            ? "Details don't match the document: " + reasons
                            : "Details don't match the document";
                    outcomes.put("DATA_CROSSCHECK", unreadable
                            ? ServiceOutcome.review("The document could not be read reliably", crosscheck)
                            : outcome(check.getStatus(), mismatch, crosscheck));
                }
                default -> { }
            }
        }
        outcomes.keySet().retainAll(services);
        return outcomes;
    }

    /** Facts known before any service: what the applicant and the client sent. */
    static Map<String, Object> startFacts(VerificationRequest request) {
        Map<String, Object> facts = new HashMap<>();
        Map<String, Object> customer = Optional.ofNullable(request.getCustomerData()).orElse(Map.of());
        putIfPresent(facts, "applicant.country", customer.get("countryOfResidence"));
        putIfPresent(facts, "applicant.nationality", customer.get("nationality"));
        facts.put("metadata", Optional.ofNullable(request.getMetadata()).orElse(Map.of()));
        return facts;
    }

    private static ServiceOutcome outcome(String status, String failureReason, Map<String, Object> facts) {
        return switch (status == null ? "" : status) {
            case "passed" -> ServiceOutcome.passed(facts);
            case "failed" -> ServiceOutcome.failed(failureReason, facts);
            default -> ServiceOutcome.review(failureReason, facts);
        };
    }

    private static void putIfPresent(Map<String, Object> facts, String key, Object value) {
        if (value != null) facts.put(key, value);
    }

    /** Some checks report "skipped_..." instead of a match result; only real booleans become facts. */
    private static void putBoolean(Map<String, Object> facts, String key, Object value) {
        if (value instanceof Boolean b) facts.put(key, b);
    }
}
