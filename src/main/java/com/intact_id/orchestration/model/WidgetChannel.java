package com.intact_id.orchestration.model;

public enum WidgetChannel {
    // Widget embedded in the company's page, token issued from the company's backend
    EMBEDDED,
    // Phone that claimed a handoff code; scoped to a single draft
    HANDOFF
}
