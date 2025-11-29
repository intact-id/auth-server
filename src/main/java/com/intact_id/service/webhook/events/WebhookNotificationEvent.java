package com.intact_id.service.webhook.events;

import com.intact_id.config.events.InternalEvent;

/**
 * Event triggered when a webhook should be sent
 */
public class WebhookNotificationEvent extends InternalEvent<WebhookEventData> {

    public WebhookNotificationEvent(Object source, WebhookEventData data) {
        super(source, data);
    }
}
