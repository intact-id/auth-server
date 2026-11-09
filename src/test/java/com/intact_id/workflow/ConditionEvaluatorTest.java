package com.intact_id.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    final Map<String, Object> facts = Map.of(
            "applicant.country", "KEN",
            "liveness.score", 0.62,
            "screening.hits", 0,
            "metadata", Map.of("tier", "gold"));

    @Test
    void evaluatesEveryOperator() {
        assertThat(matches("applicant.country", "is", "ken")).isTrue();
        assertThat(matches("applicant.country", "is_not", "UGA")).isTrue();
        assertThat(matches("applicant.country", "in", List.of("KEN", "UGA", "TZA"))).isTrue();
        assertThat(matches("applicant.country", "not_in", List.of("NGA"))).isTrue();
        assertThat(matches("liveness.score", "lt", 0.7)).isTrue();
        assertThat(matches("liveness.score", "gte", 0.7)).isFalse();
        assertThat(matches("screening.hits", "gt", 0)).isFalse();
        assertThat(matches("screening.hits", "lte", 0)).isTrue();
        assertThat(matches("metadata.tier", "is", "gold")).isTrue();
        assertThat(matches("registry.match", "exists", null)).isFalse();
    }

    @Test
    void missingFactsNeverSatisfyAComparison() {
        assertThat(matches("face.match_score", "lt", 0.75)).isFalse();
        assertThat(matches("face.match_score", "gt", 0.75)).isFalse();
    }

    @Test
    void groupsCombineOneLevelDeep() {
        Map<String, Object> condition = Map.of("all", List.of(
                Map.of("field", "applicant.country", "op", "is", "value", "KEN"),
                Map.of("any", List.of(
                        Map.of("field", "liveness.score", "op", "lt", "value", 0.5),
                        Map.of("field", "metadata.tier", "op", "is", "value", "gold")))));

        assertThat(ConditionEvaluator.validate(condition)).isEmpty();
        assertThat(ConditionEvaluator.matches(condition, facts)).isTrue();
        assertThat(ConditionEvaluator.fields(condition)).containsExactly("applicant.country", "liveness.score", "metadata.tier");
    }

    @Test
    void rejectsBadShapes() {
        assertThat(ConditionEvaluator.validate(Map.of("field", "x", "op", "between", "value", 1)))
                .anySatisfy(p -> assertThat(p).contains("unknown operator"));
        assertThat(ConditionEvaluator.validate(Map.of("field", "x", "op", "lt", "value", "high")))
                .anySatisfy(p -> assertThat(p).contains("needs a number"));
        assertThat(ConditionEvaluator.validate(Map.of("field", "x", "op", "in", "value", List.of())))
                .anySatisfy(p -> assertThat(p).contains("needs a list"));
        Map<String, Object> tooDeep = Map.of("all", List.of(Map.of("any", List.of(Map.of("all", List.of(
                Map.of("field", "x", "op", "exists")))))));
        assertThat(ConditionEvaluator.validate(tooDeep)).anySatisfy(p -> assertThat(p).contains("one level"));
    }

    private boolean matches(String field, String op, Object value) {
        Map<String, Object> condition = value == null ? Map.of("field", field, "op", op)
                : Map.of("field", field, "op", op, "value", value);
        return ConditionEvaluator.matches(condition, facts);
    }
}
