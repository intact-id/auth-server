package com.intact_id.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Evaluates workflow conditions: {"all": [...]}, {"any": [...]} (one level of nesting) or a leaf
 * {"field": "...", "op": "...", "value": ...}. Facts are a flat map ("liveness.score" -> 0.91); "metadata.x" also
 * reads nested maps. No expressions or merchant code, so every decision can be explained.
 */
public final class ConditionEvaluator {

    private static final int MAX_DEPTH = 2;

    private ConditionEvaluator() {
    }

    public static boolean matches(Map<String, Object> condition, Map<String, Object> facts) {
        if (condition.containsKey("all")) {
            return children(condition, "all").stream().allMatch(c -> matches(c, facts));
        }
        if (condition.containsKey("any")) {
            return children(condition, "any").stream().anyMatch(c -> matches(c, facts));
        }
        Object actual = fact(facts, (String) condition.get("field"));
        Object expected = condition.get("value");
        Optional<ConditionOperator> op = ConditionOperator.parse(condition.get("op"));
        if (op.isEmpty()) return false;
        return switch (op.get()) {
            case EXISTS -> actual != null;
            case IS -> sameValue(actual, expected);
            case IS_NOT -> !sameValue(actual, expected);
            case IN -> expected instanceof Collection<?> list && list.stream().anyMatch(v -> sameValue(actual, v));
            case NOT_IN -> expected instanceof Collection<?> list && list.stream().noneMatch(v -> sameValue(actual, v));
            case LT -> compares(actual, expected, r -> r < 0);
            case LTE -> compares(actual, expected, r -> r <= 0);
            case GT -> compares(actual, expected, r -> r > 0);
            case GTE -> compares(actual, expected, r -> r >= 0);
        };
    }

    /** Problems with a condition's shape (operators, values, nesting); empty when it can be evaluated. */
    public static List<String> validate(Map<String, Object> condition) {
        List<String> problems = new ArrayList<>();
        validate(condition, 0, problems);
        return problems;
    }

    /** Every field a condition reads. */
    public static Set<String> fields(Map<String, Object> condition) {
        Set<String> fields = new LinkedHashSet<>();
        collectFields(condition, fields);
        return fields;
    }

    private static void validate(Map<String, Object> condition, int depth, List<String> problems) {
        if (condition == null || condition.isEmpty()) {
            problems.add("condition is empty");
            return;
        }
        for (String group : List.of("all", "any")) {
            if (condition.containsKey(group)) {
                if (depth >= MAX_DEPTH) {
                    problems.add("conditions can be grouped at most one level deep");
                    return;
                }
                if (!(condition.get(group) instanceof List<?> items) || items.isEmpty()) {
                    problems.add("\"" + group + "\" needs a non-empty list");
                    return;
                }
                for (Object item : items) {
                    if (item instanceof Map<?, ?> child) {
                        validate(asMap(child), depth + 1, problems);
                    } else {
                        problems.add("\"" + group + "\" items must be conditions");
                    }
                }
                return;
            }
        }
        Object field = condition.get("field");
        Object value = condition.get("value");
        if (!(field instanceof String f) || f.isBlank()) {
            problems.add("condition needs a field");
        }
        Optional<ConditionOperator> parsed = ConditionOperator.parse(condition.get("op"));
        if (parsed.isEmpty()) {
            problems.add("unknown operator " + condition.get("op") + "; use one of "
                    + Arrays.stream(ConditionOperator.values()).map(ConditionOperator::json).toList());
            return;
        }
        ConditionOperator op = parsed.get();
        switch (op) {
            case EXISTS -> { }
            case IN, NOT_IN -> {
                if (!(value instanceof Collection<?> list) || list.isEmpty()) problems.add(op.json() + " needs a list of values");
            }
            case LT, LTE, GT, GTE -> {
                if (!(value instanceof Number)) problems.add(op.json() + " needs a number");
            }
            case IS, IS_NOT -> {
                if (value == null) problems.add(op.json() + " needs a value");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectFields(Map<String, Object> condition, Set<String> fields) {
        for (String group : List.of("all", "any")) {
            if (condition.get(group) instanceof List<?> items) {
                items.stream().filter(Map.class::isInstance).forEach(c -> collectFields((Map<String, Object>) c, fields));
                return;
            }
        }
        if (condition.get("field") instanceof String field) {
            fields.add(field);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> condition, String group) {
        return ((List<Object>) condition.get(group)).stream().map(c -> (Map<String, Object>) c).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static Object fact(Map<String, Object> facts, String field) {
        if (field == null) return null;
        if (facts.containsKey(field)) return facts.get(field);
        Object current = facts;
        for (String part : field.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = ((Map<String, Object>) map).get(part);
        }
        return current;
    }

    private static boolean sameValue(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number e) {
            return Double.compare(a.doubleValue(), e.doubleValue()) == 0;
        }
        if (actual instanceof String a && expected instanceof String e) {
            return a.equalsIgnoreCase(e);
        }
        return Objects.equals(actual, expected);
    }

    /** Missing or non-numeric facts never satisfy a comparison. */
    private static boolean compares(Object actual, Object expected, IntPredicate test) {
        return actual instanceof Number a && expected instanceof Number e
                && test.test(Double.compare(a.doubleValue(), e.doubleValue()));
    }
}
