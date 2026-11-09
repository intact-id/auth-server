package com.intact_id.workflow;

import com.intact_id.workflow.WorkflowGraph.Edge;
import com.intact_id.workflow.WorkflowGraph.Node;
import com.intact_id.workflow.WorkflowRun.Decision;
import com.intact_id.workflow.WorkflowRun.Step;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.intact_id.workflow.WorkflowGraph.BRANCH_PREFIX;
import static com.intact_id.workflow.WorkflowGraph.PORT_ELSE;
import static com.intact_id.workflow.WorkflowGraph.PORT_FAILED;
import static com.intact_id.workflow.WorkflowGraph.PORT_NEXT;
import static com.intact_id.workflow.WorkflowGraph.PORT_PASSED;
import static com.intact_id.workflow.WorkflowGraph.PORT_REVIEW;

/**
 * Walks a published workflow with the results of its services. Anything unexpected (a missing connection, a
 * service with no result, a loop) stops at manual review: a person decides instead of the workflow guessing.
 */
public final class WorkflowExecutor {

    private WorkflowExecutor() {
    }

    public static WorkflowRun walk(WorkflowGraph graph, Map<String, ServiceOutcome> outcomes, Map<String, Object> startFacts) {
        Map<String, Node> nodes = new HashMap<>();
        graph.nodes().forEach(n -> nodes.put(n.id(), n));
        Map<String, Map<String, String>> out = new HashMap<>();
        for (Edge e : graph.edges()) {
            out.computeIfAbsent(e.from(), k -> new HashMap<>()).put(e.port(), e.to());
        }

        Map<String, Object> facts = new HashMap<>(startFacts);
        outcomes.values().forEach(o -> facts.putAll(o.facts()));
        List<Step> path = new ArrayList<>();
        String failure = null;

        Optional<Node> start = graph.nodes().stream().filter(n -> n.type() == NodeType.START).findFirst();
        if (start.isEmpty()) {
            return new WorkflowRun(Decision.MANUAL_REVIEW, "Workflow has no Start node", path);
        }
        Node node = start.get();
        for (int guard = 0; guard <= nodes.size(); guard++) {
            Map<String, String> ports = out.getOrDefault(node.id(), Map.of());
            switch (node.type()) {
                case END -> {
                    path.add(new Step(node.id(), node.type(), null, null));
                    return node.outcome() == EndOutcome.APPROVED
                            ? new WorkflowRun(Decision.APPROVED, null, path)
                            : new WorkflowRun(Decision.REJECTED, failure != null ? failure : node.label(), path);
                }
                case MANUAL_REVIEW -> {
                    path.add(new Step(node.id(), node.type(), null, null));
                    return new WorkflowRun(Decision.MANUAL_REVIEW, failure != null ? failure : node.label(), path);
                }
                case START -> {
                    path.add(new Step(node.id(), node.type(), null, PORT_NEXT));
                    String next = ports.get(PORT_NEXT);
                    if (next == null) return stop(path, "Start is not connected");
                    node = nodes.get(next);
                }
                case SERVICE -> {
                    ServiceOutcome outcome = outcomes.get(node.service());
                    if (outcome == null) {
                        path.add(new Step(node.id(), node.type(), node.service(), null));
                        return stop(path, node.service() + " produced no result");
                    }
                    String port = switch (outcome.status()) {
                        case PASSED -> PORT_PASSED;
                        case FAILED -> PORT_FAILED;
                        case REVIEW -> PORT_REVIEW;
                    };
                    path.add(new Step(node.id(), node.type(), node.service(), port));
                    String next = ports.get(port);
                    if (next == null) {
                        // Unconnected failed/review outputs mean: reject / send to a reviewer
                        return switch (outcome.status()) {
                            case FAILED -> new WorkflowRun(Decision.REJECTED, reasonOf(node, outcome), path);
                            case REVIEW -> new WorkflowRun(Decision.MANUAL_REVIEW, reasonOf(node, outcome), path);
                            case PASSED -> stop(path, node.service() + " passed but nothing follows it");
                        };
                    }
                    if (outcome.status() != ServiceOutcome.Status.PASSED) {
                        failure = reasonOf(node, outcome);
                    }
                    node = nodes.get(next);
                }
                case CONDITION -> {
                    String port = PORT_ELSE;
                    List<WorkflowGraph.Branch> branches = node.branches() == null ? List.of() : node.branches();
                    for (int i = 0; i < branches.size(); i++) {
                        if (branches.get(i).when() != null && ConditionEvaluator.matches(branches.get(i).when(), facts)) {
                            port = BRANCH_PREFIX + i;
                            break;
                        }
                    }
                    path.add(new Step(node.id(), node.type(), null, port));
                    String next = ports.get(port);
                    if (next == null) return stop(path, "Condition " + node.id() + " has no " + port + " connection");
                    node = nodes.get(next);
                }
            }
            if (node == null) return stop(path, "A connection points to a missing step");
        }
        return stop(path, "The workflow loops");
    }

    private static WorkflowRun stop(List<Step> path, String reason) {
        return new WorkflowRun(Decision.MANUAL_REVIEW, reason, path);
    }

    private static String reasonOf(Node node, ServiceOutcome outcome) {
        return outcome.reason() != null ? outcome.reason() : node.service() + " " + outcome.status().name().toLowerCase();
    }
}
