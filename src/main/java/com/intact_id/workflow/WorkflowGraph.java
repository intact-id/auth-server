package com.intact_id.workflow;

import java.util.List;
import java.util.Map;

/** A workflow as the builder edits it: nodes joined by edges from named ports. */
public record WorkflowGraph(List<Node> nodes, List<Edge> edges) {

    public static final String PORT_NEXT = "next";
    public static final String PORT_PASSED = "passed";
    public static final String PORT_FAILED = "failed";
    public static final String PORT_REVIEW = "review";
    public static final String PORT_ELSE = "else";
    public static final String BRANCH_PREFIX = "branch:";

    public WorkflowGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    /**
     * @param service  catalog code, for SERVICE nodes
     * @param settings per-step settings, checked against the service's settings schema
     * @param branches for CONDITION nodes, tested in order
     * @param outcome  for END nodes
     * @param position canvas coordinates, kept for the builder only
     */
    public record Node(String id, NodeType type, String label, String service, Map<String, Object> settings,
                       List<Branch> branches, EndOutcome outcome, Map<String, Object> position) {
    }

    /** when: {"all": [...]}, {"any": [...]} or {"field": "liveness.score", "op": "lt", "value": 0.7} */
    public record Branch(String label, Map<String, Object> when) {
    }

    public record Edge(String from, String port, String to) {
    }
}
