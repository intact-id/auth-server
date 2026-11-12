package com.intact_id.orchestration.verification;

import com.intact_id.checks.RiskScreening;
import com.intact_id.workflow.ServiceOutcome;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    @Test
    void screeningAndRiskAssessmentBecomeFacts() {
        RiskScreening.Screening screening = new RiskScreening.Screening(RiskScreening.Verdict.POTENTIAL_MATCH, List.of(
                new RiskScreening.Match("Q1", "Jane Doe", 0.82, List.of("ke_pep"), List.of("role.pep"))));
        RiskScreening.Assessment assessment = new RiskScreening.Assessment("a-1", RiskScreening.RiskLevel.MEDIUM,
                new BigDecimal("42.5"), true, false, 3, screening);
        ExternalKycServiceClient.RiskChecks risk = new ExternalKycServiceClient.RiskChecks(screening, null, assessment, null);
        ExternalKycServiceClient.VerificationResult result = ExternalKycServiceClient.VerificationResult.builder()
                .checks(List.of(ExternalKycServiceClient.screeningCheck(risk), ExternalKycServiceClient.assessmentCheck(risk)))
                .build();

        Map<String, ServiceOutcome> outcomes = WorkflowOutcomes.from(result, Set.of("SANCTIONS_SCREENING", "CRA"));

        assertThat(outcomes.get("SANCTIONS_SCREENING").status()).isEqualTo(ServiceOutcome.Status.REVIEW);
        assertThat(outcomes.get("SANCTIONS_SCREENING").facts())
                .containsEntry("screening.verdict", "POTENTIAL_MATCH").containsEntry("screening.hits", 1)
                .containsEntry("screening.top_score", 0.82).containsEntry("screening.pep", true)
                .containsEntry("screening.adverse_media", false);
        assertThat(outcomes.get("CRA").status()).isEqualTo(ServiceOutcome.Status.PASSED);
        assertThat(outcomes.get("CRA").facts()).containsEntry("risk.level", "MEDIUM").containsEntry("risk.score", 42.5)
                .containsEntry("risk.complete", true);
    }

    @Test
    void highRiskConfirmedMatchesAndMissingModelsAreNeverPassed() {
        RiskScreening.Screening confirmed = new RiskScreening.Screening(RiskScreening.Verdict.CONFIRMED_MATCH, List.of(
                new RiskScreening.Match("Q2", "John Roe", 0.99, List.of("un_sc_sanctions"), List.of("sanction"))));
        RiskScreening.Assessment high = new RiskScreening.Assessment("a-2", RiskScreening.RiskLevel.HIGH,
                new BigDecimal("91"), true, false, 1, confirmed);
        ExternalKycServiceClient.RiskChecks risk = new ExternalKycServiceClient.RiskChecks(confirmed, null, high, null);
        ExternalKycServiceClient.RiskChecks noModel = new ExternalKycServiceClient.RiskChecks(null, null, null, "no_active_risk_model");

        Map<String, ServiceOutcome> outcomes = WorkflowOutcomes.from(ExternalKycServiceClient.VerificationResult.builder()
                .checks(List.of(ExternalKycServiceClient.screeningCheck(risk), ExternalKycServiceClient.assessmentCheck(risk)))
                .build(), Set.of("SANCTIONS_SCREENING", "CRA"));
        Map<String, ServiceOutcome> missing = WorkflowOutcomes.from(ExternalKycServiceClient.VerificationResult.builder()
                .checks(List.of(ExternalKycServiceClient.assessmentCheck(noModel))).build(), Set.of("CRA"));

        assertThat(outcomes.get("SANCTIONS_SCREENING").status()).isEqualTo(ServiceOutcome.Status.FAILED);
        assertThat(outcomes.get("SANCTIONS_SCREENING").facts()).containsEntry("screening.adverse_media", true);
        assertThat(outcomes.get("CRA").status()).isEqualTo(ServiceOutcome.Status.REVIEW);
        assertThat(missing.get("CRA").status()).isEqualTo(ServiceOutcome.Status.REVIEW);
        assertThat(missing.get("CRA").reason()).contains("no_active_risk_model");
    }

    private static ExternalKycServiceClient.CheckResult check(String step, String status, Map<String, Object> result) {
        return ExternalKycServiceClient.CheckResult.builder().stepName(step).status(status).result(result).build();
    }
}
