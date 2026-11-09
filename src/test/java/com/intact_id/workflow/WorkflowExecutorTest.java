package com.intact_id.workflow;

import com.intact_id.workflow.WorkflowGraph.Branch;
import com.intact_id.workflow.WorkflowRun.Decision;
import com.intact_id.workflow.WorkflowValidatorTest.Graph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutorTest {

    /** start -> doc -> face -> score (< 0.75 => review) -> approved; doc failed -> rejected. */
    final WorkflowGraph standard = new Graph()
            .service("doc", "DOCUMENT_CHECK").service("face", "FACE_MATCH")
            .condition("score", new Branch("Low", Map.of("field", "face.match_score", "op", "lt", "value", 0.75)))
            .review("review").end("ok", EndOutcome.APPROVED).end("no", EndOutcome.REJECTED)
            .edge("start", "next", "doc").edge("doc", "passed", "face").edge("doc", "failed", "no")
            .edge("face", "passed", "score").edge("score", "branch:0", "review").edge("score", "else", "ok")
            .build();

    @Test
    void approvesWhenEveryStepPassesAndRecordsThePath() {
        WorkflowRun run = WorkflowExecutor.walk(standard, Map.of(
                "DOCUMENT_CHECK", ServiceOutcome.passed(Map.of()),
                "FACE_MATCH", ServiceOutcome.passed(Map.of("face.match_score", 0.91))), Map.of());

        assertThat(run.decision()).isEqualTo(Decision.APPROVED);
        assertThat(run.path()).extracting(WorkflowRun.Step::nodeId).containsExactly("start", "doc", "face", "score", "ok");
        assertThat(run.path()).extracting(WorkflowRun.Step::port).containsExactly("next", "passed", "passed", "else", null);
    }

    @Test
    void branchesOnFacts() {
        WorkflowRun run = WorkflowExecutor.walk(standard, Map.of(
                "DOCUMENT_CHECK", ServiceOutcome.passed(Map.of()),
                "FACE_MATCH", ServiceOutcome.passed(Map.of("face.match_score", 0.6))), Map.of());

        assertThat(run.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(run.path()).extracting(WorkflowRun.Step::port).contains("branch:0");
    }

    @Test
    void followsAConnectedFailedOutputAndKeepsTheReason() {
        WorkflowRun run = WorkflowExecutor.walk(standard, Map.of(
                "DOCUMENT_CHECK", ServiceOutcome.failed("Document expired", Map.of())), Map.of());

        assertThat(run.decision()).isEqualTo(Decision.REJECTED);
        assertThat(run.reason()).isEqualTo("Document expired");
    }

    @Test
    void unconnectedFailedRejectsAndUnconnectedReviewGoesToAReviewer() {
        assertThat(WorkflowExecutor.walk(standard, Map.of(
                "DOCUMENT_CHECK", ServiceOutcome.passed(Map.of()),
                "FACE_MATCH", ServiceOutcome.failed("Face did not match", Map.of())), Map.of()).decision())
                .isEqualTo(Decision.REJECTED);
        assertThat(WorkflowExecutor.walk(standard, Map.of(
                "DOCUMENT_CHECK", ServiceOutcome.passed(Map.of()),
                "FACE_MATCH", ServiceOutcome.review("Borderline match", Map.of())), Map.of()).decision())
                .isEqualTo(Decision.MANUAL_REVIEW);
    }

    @Test
    void stopsAtManualReviewWhenAServiceHasNoResult() {
        WorkflowRun run = WorkflowExecutor.walk(standard, Map.of("DOCUMENT_CHECK", ServiceOutcome.passed(Map.of())), Map.of());

        assertThat(run.decision()).isEqualTo(Decision.MANUAL_REVIEW);
        assertThat(run.reason()).contains("FACE_MATCH produced no result");
    }

    @Test
    void conditionsCanReadStartFactsAndMetadata() {
        WorkflowGraph byCountry = new Graph()
                .condition("country", new Branch("East Africa", Map.of("all", java.util.List.of(
                        Map.of("field", "applicant.country", "op", "in", "value", java.util.List.of("KEN", "UGA")),
                        Map.of("field", "metadata.segment", "op", "is", "value", "retail")))))
                .end("ok", EndOutcome.APPROVED).end("no", EndOutcome.REJECTED)
                .edge("start", "next", "country").edge("country", "branch:0", "ok").edge("country", "else", "no")
                .build();

        assertThat(WorkflowExecutor.walk(byCountry, Map.of(),
                Map.of("applicant.country", "KEN", "metadata", Map.of("segment", "retail"))).decision()).isEqualTo(Decision.APPROVED);
        assertThat(WorkflowExecutor.walk(byCountry, Map.of(),
                Map.of("applicant.country", "NGA", "metadata", Map.of())).decision()).isEqualTo(Decision.REJECTED);
    }
}
