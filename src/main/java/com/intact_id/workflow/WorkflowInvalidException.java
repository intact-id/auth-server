package com.intact_id.workflow;

import java.util.List;

/** A workflow can't be published as it is; carries every problem so the builder can show them all at once. */
public class WorkflowInvalidException extends RuntimeException {

    private final List<WorkflowProblem> problems;

    public WorkflowInvalidException(List<WorkflowProblem> problems) {
        super("The workflow has " + problems.size() + " problem(s) to fix before it can be published");
        this.problems = List.copyOf(problems);
    }

    public List<WorkflowProblem> getProblems() {
        return problems;
    }
}
