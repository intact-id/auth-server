package com.intact_id.workflow;

import com.intact_id.workflow.WorkflowGraph.Branch;
import com.intact_id.workflow.WorkflowGraph.Edge;
import com.intact_id.workflow.WorkflowGraph.Node;
import com.intact_id.workflow.definition.WorkflowValidator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowValidatorTest {

    final ServiceCatalog catalog = mock(ServiceCatalog.class);
    final WorkflowValidator validator = new WorkflowValidator(catalog);

    WorkflowValidatorTest() {
        Map<String, CatalogService> services = Map.of(
                "DOCUMENT_CHECK", service("DOCUMENT_CHECK", ServiceStatus.AVAILABLE, List.of(), List.of("document.country"), Map.of()),
                "LIVENESS", service("LIVENESS", ServiceStatus.AVAILABLE, List.of(), List.of("liveness.score"), Map.of()),
                "FACE_MATCH", service("FACE_MATCH", ServiceStatus.AVAILABLE, List.of("DOCUMENT_CHECK", "LIVENESS"), List.of("face.match_score"), Map.of()),
                "AGE_CHECK", service("AGE_CHECK", ServiceStatus.AVAILABLE, List.of("DOCUMENT_CHECK"), List.of(), Map.of("required", List.of("minimumAge"))),
                "IPRS_LOOKUP", service("IPRS_LOOKUP", ServiceStatus.IN_DEVELOPMENT, List.of(), List.of("registry.match"), Map.of()));
        when(catalog.find(anyString())).thenAnswer(i -> Optional.ofNullable(services.get(i.<String>getArgument(0))));
    }

    @Test
    void acceptsTheStandardKycShape() {
        Graph g = new Graph()
                .service("doc", "DOCUMENT_CHECK").service("live", "LIVENESS").service("face", "FACE_MATCH")
                .condition("score", new Branch("Low score", Map.of("field", "face.match_score", "op", "lt", "value", 0.75)))
                .review("review").end("ok", EndOutcome.APPROVED).end("no", EndOutcome.REJECTED)
                .edge("start", "next", "doc").edge("doc", "passed", "live").edge("doc", "failed", "no")
                .edge("live", "passed", "face").edge("face", "passed", "score")
                .edge("score", "branch:0", "review").edge("score", "else", "ok");

        WorkflowValidator.Result result = validator.validate(g.build(), SubjectType.INDIVIDUAL);

        assertThat(result.problems()).isEmpty();
        assertThat(result.services()).containsExactly("DOCUMENT_CHECK", "LIVENESS", "FACE_MATCH");
    }

    @Test
    void aDependencyMustRunOnEveryPathNotJustOne() {
        // Kenyans get liveness, everyone else goes straight to face match: liveness isn't guaranteed
        Graph g = new Graph()
                .service("doc", "DOCUMENT_CHECK")
                .condition("country", new Branch("Kenya", Map.of("field", "applicant.country", "op", "is", "value", "KEN")))
                .service("live", "LIVENESS").service("face", "FACE_MATCH").end("ok", EndOutcome.APPROVED)
                .edge("start", "next", "doc").edge("doc", "passed", "country")
                .edge("country", "branch:0", "live").edge("country", "else", "face")
                .edge("live", "passed", "face").edge("face", "passed", "ok");

        assertThat(validator.validate(g.build(), SubjectType.INDIVIDUAL).problems())
                .anySatisfy(p -> {
                    assertThat(p.nodeId()).isEqualTo("face");
                    assertThat(p.message()).contains("needs LIVENESS to run before it on every path");
                });
    }

    @Test
    void conditionsCanOnlyReadOutputsAlreadyProduced() {
        Graph g = new Graph()
                .condition("check", new Branch("Low", Map.of("field", "liveness.score", "op", "lt", "value", 0.5)))
                .service("live", "LIVENESS").end("ok", EndOutcome.APPROVED).end("no", EndOutcome.REJECTED)
                .edge("start", "next", "check").edge("check", "branch:0", "no").edge("check", "else", "live")
                .edge("live", "passed", "ok");

        assertThat(validator.validate(g.build(), SubjectType.INDIVIDUAL).problems())
                .anySatisfy(p -> assertThat(p.message()).contains("Reads \"liveness.score\""));
    }

    @Test
    void reportsStructuralProblemsWithTheirNodes() {
        Graph g = new Graph()
                .service("doc", "DOCUMENT_CHECK").service("iprs", "IPRS_LOOKUP").service("age", "AGE_CHECK")
                .condition("cond", new Branch("x", Map.of("field", "document.country", "op", "between", "value", 1)))
                .end("ok", null).end("orphan", EndOutcome.APPROVED)
                .edge("start", "next", "doc").edge("doc", "passed", "iprs").edge("iprs", "passed", "age")
                .edge("age", "passed", "cond").edge("cond", "branch:0", "ok").edge("doc", "sideways", "ok");

        List<WorkflowProblem> problems = validator.validate(g.build(), SubjectType.INDIVIDUAL).problems();

        assertThat(problems).anySatisfy(p -> assertThat(p.message()).contains("\"sideways\" is not an output"))
                .anySatisfy(p -> assertThat(p.message()).contains("IPRS_LOOKUP is not available"))
                .anySatisfy(p -> assertThat(p.message()).contains("needs the setting \"minimumAge\""))
                .anySatisfy(p -> assertThat(p.message()).contains("unknown operator between"))
                .anySatisfy(p -> assertThat(p.message()).contains("needs an Else path"))
                .anySatisfy(p -> assertThat(p.message()).contains("Choose the End node's outcome"))
                .anySatisfy(p -> {
                    assertThat(p.nodeId()).isEqualTo("orphan");
                    assertThat(p.message()).contains("Not connected");
                });
    }

    @Test
    void rejectsLoops() {
        Graph g = new Graph().service("a", "DOCUMENT_CHECK").service("b", "LIVENESS")
                .edge("start", "next", "a").edge("a", "passed", "b").edge("b", "passed", "a");

        assertThat(validator.validate(g.build(), SubjectType.INDIVIDUAL).problems())
                .anySatisfy(p -> assertThat(p.message()).contains("loops back"));
    }

    private static CatalogService service(String code, ServiceStatus status, List<String> requires, List<String> outputs,
                                          Map<String, Object> settingsSchema) {
        return new CatalogService(code, code, null, ServiceCategory.VERIFICATION, status, List.of(SubjectType.INDIVIDUAL),
                List.of(), requires, List.of(), List.of(),
                outputs.stream().map(o -> Map.<String, Object>of("key", o, "type", "number")).toList(),
                Map.of(), settingsSchema, 0);
    }

    /** Small builder so each test reads like the canvas. */
    static class Graph {
        final List<Node> nodes = new ArrayList<>(List.of(new Node("start", NodeType.START, "Start", null, null, null, null, null)));
        final List<Edge> edges = new ArrayList<>();

        Graph service(String id, String code) {
            nodes.add(new Node(id, NodeType.SERVICE, id, code, null, null, null, null));
            return this;
        }

        Graph condition(String id, Branch... branches) {
            nodes.add(new Node(id, NodeType.CONDITION, id, null, null, List.of(branches), null, null));
            return this;
        }

        Graph review(String id) {
            nodes.add(new Node(id, NodeType.MANUAL_REVIEW, id, null, null, null, null, null));
            return this;
        }

        Graph end(String id, EndOutcome outcome) {
            nodes.add(new Node(id, NodeType.END, id, null, null, null, outcome, null));
            return this;
        }

        Graph edge(String from, String port, String to) {
            edges.add(new Edge(from, port, to));
            return this;
        }

        WorkflowGraph build() {
            return new WorkflowGraph(nodes, edges);
        }
    }
}
