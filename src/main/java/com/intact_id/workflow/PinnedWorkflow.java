package com.intact_id.workflow;

import java.util.List;

/** The workflow version a session runs; pinned when the session starts, so later edits never change a run. */
public record PinnedWorkflow(
        String workflowId,
        String key,
        int version,
        SubjectType subjectType,
        WorkflowGraph graph,
        List<String> services,
        ServiceRequirements requirements) {

    public boolean runs(String serviceCode) {
        return services.contains(serviceCode);
    }
}
