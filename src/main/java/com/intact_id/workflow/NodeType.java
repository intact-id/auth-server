package com.intact_id.workflow;

/**
 * START: entry (port "next"). SERVICE: runs a catalog service (ports "passed", optional "failed" and "review").
 * CONDITION: first matching branch wins (ports "branch:0", "branch:1", ... and a required "else").
 * MANUAL_REVIEW: opens a review case and ends the automated path. END: final outcome APPROVED or REJECTED.
 */
public enum NodeType {
    START,
    SERVICE,
    CONDITION,
    MANUAL_REVIEW,
    END
}
