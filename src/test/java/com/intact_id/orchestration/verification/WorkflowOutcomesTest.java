package com.intact_id.orchestration.verification;

import com.intact_id.workflow.ServiceOutcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowOutcomesTest {

    @Test
    void mapsEachCheckToItsCatalogServicesAndFacts() {
        ExternalKycServiceClient.VerificationResult result = ExternalKycServiceClient.VerificationResult.builder()
                .checks(List.of(
                        check("face_match", "passed", Map.of("match_score", 0.88, "verified", true)),
                        check("liveness_detection", "failed", Map.of("liveness_score", 41.0, "passed", false)),
                        check("ocr_extraction", "failed", Map.of("document_type", "national_id", "overall_confidence", 0.93,
                                "name_match", false, "id_number_match", true, "dob_match", "skipped_parse_error",
                                "failure_reasons", List.of("name_mismatch")))))
                .extractedOcrData(Map.of("country_iso3", "KEN"))
                .build();

        Map<String, ServiceOutcome> outcomes = WorkflowOutcomes.from(result,
                Set.of("FACE_MATCH", "LIVENESS", "DOCUMENT_CHECK", "DATA_CROSSCHECK"));

        assertThat(outcomes.get("FACE_MATCH").status()).isEqualTo(ServiceOutcome.Status.PASSED);
        assertThat(outcomes.get("FACE_MATCH").facts()).containsEntry("face.match_score", 0.88).containsEntry("face.matched", true);
        assertThat(outcomes.get("LIVENESS").status()).isEqualTo(ServiceOutcome.Status.FAILED);
        assertThat(outcomes.get("LIVENESS").facts()).containsEntry("liveness.score", 0.41);
        assertThat(outcomes.get("DOCUMENT_CHECK").status()).isEqualTo(ServiceOutcome.Status.PASSED);
        assertThat(outcomes.get("DOCUMENT_CHECK").facts()).containsEntry("document.country", "KEN").containsEntry("document.type", "national_id");
        assertThat(outcomes.get("DATA_CROSSCHECK").status()).isEqualTo(ServiceOutcome.Status.FAILED);
        assertThat(outcomes.get("DATA_CROSSCHECK").reason()).contains("name_mismatch");
        assertThat(outcomes.get("DATA_CROSSCHECK").facts())
                .containsEntry("crosscheck.name_match", false).containsEntry("crosscheck.id_number_match", true)
                .doesNotContainKey("crosscheck.dob_match");
    }

    @Test
    void unreadableDocumentsGoToReviewAndOnlyTheWorkflowsServicesAreReturned() {
        ExternalKycServiceClient.VerificationResult result = ExternalKycServiceClient.VerificationResult.builder()
                .checks(List.of(
                        check("ocr_extraction", "review", Map.of("reason", "low_ocr_confidence", "overall_confidence", 0.3)),
                        check("liveness_detection", "skipped", Map.of("reason", "liveness_service_unavailable"))))
                .build();

        Map<String, ServiceOutcome> outcomes = WorkflowOutcomes.from(result, Set.of("DOCUMENT_CHECK", "LIVENESS"));

        assertThat(outcomes).containsOnlyKeys("DOCUMENT_CHECK", "LIVENESS");
        assertThat(outcomes.get("DOCUMENT_CHECK").status()).isEqualTo(ServiceOutcome.Status.REVIEW);
        assertThat(outcomes.get("LIVENESS").status()).isEqualTo(ServiceOutcome.Status.REVIEW);
    }

    private static ExternalKycServiceClient.CheckResult check(String step, String status, Map<String, Object> result) {
        return ExternalKycServiceClient.CheckResult.builder().stepName(step).status(status).result(result).build();
    }
}
