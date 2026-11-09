package com.intact_id.workflow.definition;

import com.intact_id.workflow.CatalogService;
import com.intact_id.workflow.ConditionEvaluator;
import com.intact_id.workflow.NodeType;
import com.intact_id.workflow.ServiceCatalog;
import com.intact_id.workflow.ServiceStatus;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.WorkflowGraph;
import com.intact_id.workflow.WorkflowGraph.Edge;
import com.intact_id.workflow.WorkflowGraph.Node;
import com.intact_id.workflow.WorkflowProblem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.intact_id.workflow.WorkflowGraph.BRANCH_PREFIX;
import static com.intact_id.workflow.WorkflowGraph.PORT_ELSE;
import static com.intact_id.workflow.WorkflowGraph.PORT_FAILED;
import static com.intact_id.workflow.WorkflowGraph.PORT_NEXT;
import static com.intact_id.workflow.WorkflowGraph.PORT_PASSED;
import static com.intact_id.workflow.WorkflowGraph.PORT_REVIEW;

/** Checks a workflow graph against the catalog; a workflow with problems can't be published. */
@Component
@RequiredArgsConstructor
public class WorkflowValidator {

    /** Facts known before any service runs: what the applicant or client sent when the run started. */
    static final Set<String> START_FIELDS = Set.of("applicant.country", "applicant.nationality", "applicant.subjectType");
    static final String METADATA_PREFIX = "metadata.";

    private final ServiceCatalog catalog;

    public record Result(List<WorkflowProblem> problems, List<String> services) {
        public boolean valid() {
            return problems.isEmpty();
        }
    }

    public Result validate(WorkflowGraph graph, SubjectType subjectType) {
        List<WorkflowProblem> problems = new ArrayList<>();
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            if (node.id() == null || node.id().isBlank()) {
                problems.add(new WorkflowProblem(null, "Every node needs an id"));
            } else if (nodes.putIfAbsent(node.id(), node) != null) {
                problems.add(new WorkflowProblem(node.id(), "Duplicate node id " + node.id()));
            }
            if (node.type() == null) {
                problems.add(new WorkflowProblem(node.id(), "Node has no type"));
            }
        }
        if (!problems.isEmpty()) {
            return new Result(problems, List.of());
        }

        Map<String, Map<String, String>> out = edges(graph, nodes, problems);
        List<Node> starts = nodes.values().stream().filter(n -> n.type() == NodeType.START).toList();
        if (starts.size() != 1) {
            problems.add(new WorkflowProblem(null, "A workflow needs exactly one Start node (found " + starts.size() + ")"));
            return new Result(problems, List.of());
        }
        Node start = starts.get(0);

        for (Node node : nodes.values()) {
            checkNode(node, out.getOrDefault(node.id(), Map.of()), subjectType, problems);
        }

        Set<String> reachable = reachable(start.id(), out);
        nodes.keySet().stream().filter(id -> !reachable.contains(id))
                .forEach(id -> problems.add(new WorkflowProblem(id, "Not connected to the Start node")));

        Optional<String> cycleAt = findCycle(start.id(), out);
        if (cycleAt.isPresent()) {
            problems.add(new WorkflowProblem(cycleAt.get(), "The workflow loops back on itself"));
            return new Result(problems, List.of());
        }

        List<String> order = topologicalOrder(start.id(), out);
        checkPaths(order, nodes, out, problems);

        List<String> services = order.stream().map(nodes::get)
                .filter(n -> n.type() == NodeType.SERVICE && n.service() != null)
                .map(Node::service).distinct().toList();
        if (services.isEmpty()) {
            problems.add(new WorkflowProblem(null, "Add at least one service"));
        }
        return new Result(problems, services);
    }

    private Map<String, Map<String, String>> edges(WorkflowGraph graph, Map<String, Node> nodes, List<WorkflowProblem> problems) {
        Map<String, Map<String, String>> out = new HashMap<>();
        for (Edge edge : graph.edges()) {
            Node from = nodes.get(edge.from());
            if (from == null || !nodes.containsKey(edge.to())) {
                problems.add(new WorkflowProblem(edge.from(), "Connection to or from a missing node"));
                continue;
            }
            if (nodes.get(edge.to()).type() == NodeType.START) {
                problems.add(new WorkflowProblem(edge.from(), "Nothing can connect into the Start node"));
                continue;
            }
            if (!allowedPort(from, edge.port())) {
                problems.add(new WorkflowProblem(from.id(), "\"" + edge.port() + "\" is not an output of a " + from.type() + " node"));
                continue;
            }
            if (out.computeIfAbsent(from.id(), k -> new LinkedHashMap<>()).put(edge.port(), edge.to()) != null) {
                problems.add(new WorkflowProblem(from.id(), "Output \"" + edge.port() + "\" is connected twice"));
            }
        }
        return out;
    }

    private static boolean allowedPort(Node node, String port) {
        if (port == null) return false;
        return switch (node.type()) {
            case START -> PORT_NEXT.equals(port);
            case SERVICE -> Set.of(PORT_PASSED, PORT_FAILED, PORT_REVIEW).contains(port);
            case CONDITION -> PORT_ELSE.equals(port) || branchIndex(port)
                    .filter(i -> node.branches() != null && i < node.branches().size()).isPresent();
            case MANUAL_REVIEW, END -> false;
        };
    }

    private void checkNode(Node node, Map<String, String> ports, SubjectType subjectType, List<WorkflowProblem> problems) {
        switch (node.type()) {
            case START -> {
                if (!ports.containsKey(PORT_NEXT)) problems.add(new WorkflowProblem(node.id(), "Connect the Start node to a first step"));
            }
            case SERVICE -> {
                Optional<CatalogService> service = Optional.ofNullable(node.service()).flatMap(catalog::find);
                if (service.isEmpty()) {
                    problems.add(new WorkflowProblem(node.id(), "Unknown service " + node.service()));
                } else {
                    CatalogService s = service.get();
                    if (s.status() != ServiceStatus.AVAILABLE) {
                        problems.add(new WorkflowProblem(node.id(), s.name() + " is not available (" + s.status() + ")"));
                    }
                    if (!s.supportsSubject(subjectType)) {
                        problems.add(new WorkflowProblem(node.id(), s.name() + " does not support " + subjectType + " applicants"));
                    }
                    if (s.settingsSchema().get("required") instanceof List<?> required) {
                        Map<String, Object> settings = Optional.ofNullable(node.settings()).orElse(Map.of());
                        required.stream().map(String::valueOf).filter(k -> !settings.containsKey(k))
                                .forEach(k -> problems.add(new WorkflowProblem(node.id(), s.name() + " needs the setting \"" + k + "\"")));
                    }
                }
                if (!ports.containsKey(PORT_PASSED)) {
                    problems.add(new WorkflowProblem(node.id(), "Connect what happens when this step passes"));
                }
            }
            case CONDITION -> {
                if (node.branches() == null || node.branches().isEmpty()) {
                    problems.add(new WorkflowProblem(node.id(), "A condition needs at least one branch"));
                    return;
                }
                for (int i = 0; i < node.branches().size(); i++) {
                    String label = Optional.ofNullable(node.branches().get(i).label()).orElse("Branch " + (i + 1));
                    ConditionEvaluator.validate(node.branches().get(i).when())
                            .forEach(p -> problems.add(new WorkflowProblem(node.id(), label + ": " + p)));
                    if (!ports.containsKey(BRANCH_PREFIX + i)) {
                        problems.add(new WorkflowProblem(node.id(), "Connect branch \"" + label + "\""));
                    }
                }
                if (!ports.containsKey(PORT_ELSE)) {
                    problems.add(new WorkflowProblem(node.id(), "Every condition needs an Else path"));
                }
            }
            case END -> {
                if (node.outcome() == null) {
                    problems.add(new WorkflowProblem(node.id(), "Choose the End node's outcome (APPROVED or REJECTED)"));
                }
            }
            case MANUAL_REVIEW -> { }
        }
    }

    /**
     * Walks the graph in order, tracking which services have run on every path into each node: a service's
     * dependencies, and the outputs a condition reads, must be guaranteed whichever way the applicant came.
     */
    private void checkPaths(List<String> order, Map<String, Node> nodes, Map<String, Map<String, String>> out,
                            List<WorkflowProblem> problems) {
        Map<String, Set<String>> ranBefore = new HashMap<>();
        ranBefore.put(order.get(0), Set.of());
        for (String id : order) {
            Node node = nodes.get(id);
            Set<String> ran = ranBefore.getOrDefault(id, Set.of());
            if (node.type() == NodeType.SERVICE && node.service() != null) {
                catalog.find(node.service()).ifPresent(s -> s.requires().stream()
                        .filter(dependency -> !ran.contains(dependency))
                        .forEach(dependency -> problems.add(new WorkflowProblem(id,
                                s.name() + " needs " + name(dependency) + " to run before it on every path"))));
            }
            if (node.type() == NodeType.CONDITION && node.branches() != null) {
                Set<String> known = outputsOf(ran);
                node.branches().stream()
                        .filter(b -> b.when() != null)
                        .flatMap(b -> ConditionEvaluator.fields(b.when()).stream())
                        .distinct()
                        .filter(field -> !known.contains(field) && !START_FIELDS.contains(field) && !field.startsWith(METADATA_PREFIX))
                        .forEach(field -> problems.add(new WorkflowProblem(id,
                                "Reads \"" + field + "\", which no earlier step produces on every path")));
            }
            Set<String> after = new HashSet<>(ran);
            if (node.type() == NodeType.SERVICE && node.service() != null) after.add(node.service());
            for (String next : out.getOrDefault(id, Map.of()).values()) {
                ranBefore.merge(next, after, (a, b) -> {
                    Set<String> both = new HashSet<>(a);
                    both.retainAll(b);
                    return both;
                });
            }
        }
    }

    private Set<String> outputsOf(Set<String> services) {
        Set<String> outputs = new HashSet<>();
        services.forEach(code -> catalog.find(code).ifPresent(s -> s.outputs().stream()
                .map(o -> o.get("key")).filter(Objects::nonNull).map(String::valueOf).forEach(outputs::add)));
        return outputs;
    }

    private String name(String code) {
        return catalog.find(code).map(CatalogService::name).orElse(code);
    }

    private static Set<String> reachable(String start, Map<String, Map<String, String>> out) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (seen.add(id)) queue.addAll(out.getOrDefault(id, Map.of()).values());
        }
        return seen;
    }

    private static Optional<String> findCycle(String start, Map<String, Map<String, String>> out) {
        return cycleFrom(start, out, new HashSet<>(), new HashSet<>());
    }

    private static Optional<String> cycleFrom(String id, Map<String, Map<String, String>> out, Set<String> onPath, Set<String> done) {
        if (onPath.contains(id)) return Optional.of(id);
        if (!done.add(id)) return Optional.empty();
        onPath.add(id);
        for (String next : out.getOrDefault(id, Map.of()).values()) {
            Optional<String> cycle = cycleFrom(next, out, onPath, done);
            if (cycle.isPresent()) return cycle;
        }
        onPath.remove(id);
        return Optional.empty();
    }

    /** Reachable nodes, each after everything that leads into it. */
    private static List<String> topologicalOrder(String start, Map<String, Map<String, String>> out) {
        Set<String> reachable = reachable(start, out);
        Map<String, Integer> incoming = new HashMap<>();
        reachable.forEach(id -> incoming.putIfAbsent(id, 0));
        reachable.forEach(id -> out.getOrDefault(id, Map.of()).values().forEach(next -> incoming.merge(next, 1, Integer::sum)));
        List<String> order = new ArrayList<>();
        Deque<String> ready = new ArrayDeque<>(List.of(start));
        while (!ready.isEmpty()) {
            String id = ready.poll();
            order.add(id);
            for (String next : out.getOrDefault(id, Map.of()).values()) {
                if (incoming.merge(next, -1, Integer::sum) == 0) ready.add(next);
            }
        }
        return order;
    }

    private static Optional<Integer> branchIndex(String port) {
        if (!port.startsWith(BRANCH_PREFIX)) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(port.substring(BRANCH_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
