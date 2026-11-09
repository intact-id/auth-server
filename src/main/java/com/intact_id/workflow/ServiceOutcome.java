package com.intact_id.workflow;

import java.util.Map;

/** What one service produced in a run: which output port it takes, and the facts conditions can read. */
public record ServiceOutcome(Status status, String reason, Map<String, Object> facts) {

    public enum Status { PASSED, FAILED, REVIEW }

    public static ServiceOutcome passed(Map<String, Object> facts) {
        return new ServiceOutcome(Status.PASSED, null, facts);
    }

    public static ServiceOutcome failed(String reason, Map<String, Object> facts) {
        return new ServiceOutcome(Status.FAILED, reason, facts);
    }

    public static ServiceOutcome review(String reason, Map<String, Object> facts) {
        return new ServiceOutcome(Status.REVIEW, reason, facts);
    }
}
