package com.intact_id.config.kafka;

/**
 * Topic naming convention for domain events published to Kafka. Mirrors the existing
 * InternalEvent domains (webhook, verification, billing) so extracting a consumer for any one
 * of them later is a 1:1 mapping.
 *
 * auth-server is a producer only — it does not consume from any of these topics itself.
 * BILLING_EVENTS is published by BillingEventPublisher, keyed by clientIdentifier
 * (Company.companyIdentifierCode), payload shape in BillingEventMessage. WEBHOOK_NOTIFICATIONS
 * and VERIFICATION_STATUS_CHANGED are reserved names, not wired to anything yet.
 *
 * Consumer-side naming convention (owned/configured by the consuming service, not here):
 * group id "<consumer-service-name>.<purpose>", e.g. "billing-service.kyc-usage-ingest".
 */
public final class KafkaTopics {

    private static final String PREFIX = "intact-id.";

    public static final String WEBHOOK_NOTIFICATIONS = PREFIX + "webhook.notifications";
    public static final String VERIFICATION_STATUS_CHANGED = PREFIX + "verification.status-changed";
    public static final String BILLING_EVENTS = PREFIX + "billing.events";

    private KafkaTopics() {
    }
}
