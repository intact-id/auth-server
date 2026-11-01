package com.intact_id.security.authentication;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.kyc.WidgetChannel;

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
        WidgetChannel channel) {

    public static final String ATTRIBUTE = "widget.context";
}
