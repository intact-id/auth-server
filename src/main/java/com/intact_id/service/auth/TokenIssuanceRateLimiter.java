package com.intact_id.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intact_id.config.CompanyAccessTokenConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for POST /api/v1/auth/token, keyed by apiKey+IP.
 *
 * This endpoint concentrates the expensive credential-validation path (DB lookup + secure
 * hash comparison) that used to run on every /verify call, which also makes it the
 * brute-force target now. There's no existing generic per-key/IP rate limiter in the repo to
 * reuse (RateLimitService is specific to counting VerificationRequest rows per company/
 * environment) — this follows the same "window + limit" shape but is Caffeine-backed so a
 * flood of bad attempts costs no DB reads/writes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenIssuanceRateLimiter {

    private final CompanyAccessTokenConfig config;

    private Cache<String, AtomicInteger> attemptCounts;

    @PostConstruct
    void init() {
        attemptCounts = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(config.getRateLimitWindowSeconds()))
                .maximumSize(100_000)
                .build();
    }

    /**
     * @return true if the request is within the rate limit (and has been counted against it),
     *         false if the limit is already exceeded (not counted further).
     */
    public boolean tryConsume(String apiKey, String clientIp) {
        String key = (apiKey != null ? apiKey : "unknown") + "|" + (clientIp != null ? clientIp : "unknown");
        AtomicInteger count = attemptCounts.get(key, k -> new AtomicInteger(0));
        int used = count.incrementAndGet();
        if (used > config.getRateLimitMaxAttempts()) {
            log.warn("Token issuance rate limit exceeded for {} ({}/{})", key, used, config.getRateLimitMaxAttempts());
            return false;
        }
        return true;
    }
}
