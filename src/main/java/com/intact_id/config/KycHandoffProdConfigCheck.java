package com.intact_id.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails startup in prod if the phone handoff link would point users at a developer machine.
 */
@Configuration
@Profile("prod")
@RequiredArgsConstructor
public class KycHandoffProdConfigCheck {

    private final KycHandoffConfig config;

    @PostConstruct
    void verifyMobileBaseUrl() {
        String url = config.getMobileBaseUrl();
        if (url == null || !url.startsWith("https://") || url.contains("localhost") || url.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "kyc.handoff.mobile-base-url must be a public https URL in prod (set KYC_WIDGET_MOBILE_URL), got: " + url);
        }
    }
}
