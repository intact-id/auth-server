package com.intact_id.service.kyc.handoff;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intact_id.config.KycHandoffConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window, per-IP limit on the public claim endpoint to stop code guessing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HandoffClaimRateLimiter {

    private final KycHandoffConfig config;

    private Cache<String, AtomicInteger> attemptCounts;

    @PostConstruct
    void init() {
        attemptCounts = Caffeine.newBuilder()
                .expireAfterWrite(config.getClaimWindow())
                .maximumSize(100_000)
                .build();
    }

    public boolean tryConsume(String clientIp) {
        String key = clientIp != null ? clientIp : "unknown";
        int used = attemptCounts.get(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (used > config.getClaimMaxAttempts()) {
            log.warn("Handoff claim rate limit exceeded for {} ({}/{})", key, used, config.getClaimMaxAttempts());
            return false;
        }
        return true;
    }
}
