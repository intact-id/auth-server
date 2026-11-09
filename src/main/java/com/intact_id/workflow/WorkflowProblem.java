package com.intact_id.workflow;

/** A reason a workflow can't be published; nodeId lets the builder highlight the box (null = whole workflow). */
public record WorkflowProblem(String nodeId, String message) {
}
