package com.intact_id.workflow;

import java.util.Optional;

/** Pins and loads published workflows for sessions and verification runs. */
public interface WorkflowRuntime {

    /**
     * The version a new session should run: the latest published one in sandbox, the live one in production.
     * Throws a bad request when the company has no such workflow or it isn't published/live yet.
     */
    PinnedWorkflow pin(String companyId, String workflowKey, boolean live);

    /**
     * The company's default workflow, for a session or API call that names none: its live version in
     * production, its latest published version in sandbox. Empty when there is no default (or nothing to run yet).
     */
    Optional<PinnedWorkflow> pinDefault(String companyId, boolean live);

    /** A pinned version, for submitting and processing a run. */
    Optional<PinnedWorkflow> find(String workflowId, int version);
}
