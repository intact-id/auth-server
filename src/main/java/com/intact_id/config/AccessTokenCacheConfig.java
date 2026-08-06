package com.intact_id.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intact_id.db.model.Company;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine (in-memory, not Redis) caches backing company access-token validation.
 *
 * - companyAccessTokenDenylist: revoked jtis only — never credentials or validation results.
 *   TTL matches the token's own expiry so an entry never needs to outlive what it's guarding.
 * - companyAccessTokenCompanyCache: resolved Company entities keyed by the opaque company id
 *   carried in the token, so validating a token doesn't require a DB hit on every request.
 *   TTL does not exceed the token's own expiry, per the caching rule for this flow.
 */
@Configuration
public class AccessTokenCacheConfig {

    @Value("${jwt.company-access-token-expiration}")
    private long companyAccessTokenExpirationMs;

    @Bean
    public Cache<String, Long> companyAccessTokenDenylist(CompanyAccessTokenConfig config) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(companyAccessTokenExpirationMs))
                .maximumSize(config.getDenylistMaxSize())
                .build();
    }

    @Bean
    public Cache<String, Company> companyAccessTokenCompanyCache(CompanyAccessTokenConfig config) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(companyAccessTokenExpirationMs))
                .maximumSize(config.getCompanyCacheMaxSize())
                .build();
    }
}
