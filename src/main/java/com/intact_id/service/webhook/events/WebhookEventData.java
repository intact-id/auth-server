package com.intact_id.service.webhook.events;

import lombok.Getter;

import java.io.Serializable;

/**
 * Event data for webhook notifications
 */
@Getter
public class WebhookEventData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String companyId;
    private final String verificationId;
    private final String eventType;
    private final Object eventData;

    public WebhookEventData(String companyId, String verificationId,
            String eventType, Object eventData) {
        this.companyId = companyId;
        this.verificationId = verificationId;
        this.eventType = eventType;
        this.eventData = eventData;
    }
}
