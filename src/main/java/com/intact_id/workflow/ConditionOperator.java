package com.intact_id.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/** Operators a condition can use; written in lowercase in the workflow JSON ("lt", "not_in", ...). */
public enum ConditionOperator {
    IS, IS_NOT, IN, NOT_IN, LT, LTE, GT, GTE, EXISTS;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ConditionOperator fromJson(String value) {
        return parse(value).orElseThrow(() -> new IllegalArgumentException("Unknown operator " + value));
    }

    public static Optional<ConditionOperator> parse(Object value) {
        if (!(value instanceof String text)) return Optional.empty();
        return Arrays.stream(values()).filter(op -> op.json().equals(text)).findFirst();
    }
}
