package com.intact_id.workflow;

import java.util.List;

/**
 * The result of walking a workflow: the decision and every node taken, for the audit trail.
 *
 * @param decision APPROVED, REJECTED or MANUAL_REVIEW
 * @param reason   why it ended there (the failed service, or a safety stop), null when approved
 */
public record WorkflowRun(Decision decision, String reason, List<Step> path) {

    public enum Decision { APPROVED, REJECTED, MANUAL_REVIEW }

    /** One node on the path and the output it left by (passed, failed, branch:0, else, ...). */
    public record Step(String nodeId, NodeType type, String service, String port) {
    }
}
