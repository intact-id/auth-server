package com.intact_id.orchestration;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.orchestration.model.WidgetChannel;

/**
 * Claims of a validated widget token, attached to the request by KycWidgetAuthFilter.
 */
public record WidgetSessionContext(
        String companyId,
        // Shown on the widget's first screen ("Acme Pay verification")
        String companyName,
        ApiKeyEnvironment environment,
        String tier,
        String externalReference,
        String draftId,
        String handoffId,
        WidgetChannel channel,
        // The workflow version pinned at session start; null for tier-based sessions
        String workflowId,
        Integer workflowVersion) {

    public static final String ATTRIBUTE = "widget.context";
}
