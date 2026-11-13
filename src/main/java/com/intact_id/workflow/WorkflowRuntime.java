package com.intact_id.workflow;

import java.util.Optional;

/** Pins and loads published workflows for sessions and verification runs. */
public interface WorkflowRuntime {

    /**
     * The version a new session or API call runs: the workflow's live version (the last one published,
     * unless an older one was put back). Throws a bad request when there is no such workflow or it was
     * never published.
     */
    PinnedWorkflow pin(String companyId, String workflowKey);

    /** The company's default workflow's live version, for a call that names none; empty when there is none. */
    Optional<PinnedWorkflow> pinDefault(String companyId);

    /** A pinned version, for submitting and processing a run. */
    Optional<PinnedWorkflow> find(String workflowId, int version);
}
