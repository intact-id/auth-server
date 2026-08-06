package com.intact_id.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "company-access-token")
public class CompanyAccessTokenConfig {

    /** Max distinct companies held in the resolved-tenant cache at once. */
    private int companyCacheMaxSize = 10_000;

    /** Max distinct revoked jtis held in the denylist at once. */
    private int denylistMaxSize = 50_000;

    /** Token-issuance rate limit window, in seconds. */
    private int rateLimitWindowSeconds = 60;

    /** Max POST /api/v1/auth/token attempts per key+IP within the window. */
    private int rateLimitMaxAttempts = 10;
}
