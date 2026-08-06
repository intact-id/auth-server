package com.intact_id.config;

import com.intact_id.service.kyc.ExternalServiceFailureClassifier;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Circuit breakers for face-service and OCR. Each downstream gets its own breaker so one
 * service degrading doesn't block calls to the other.
 *
 * Trips on transient failures (see ExternalServiceFailureClassifier) and on calls that
 * succeed but run slow — FeignConfig's read timeout is 120s, so a service limping at
 * 90s/call would never throw a timeout exception but would still stall the KYC worker pool
 * (VerificationProcessingConfig.maxConcurrent threads) one call at a time.
 */
@Configuration
public class ResilienceConfig {

    public static final String FACE_SERVICE = "face-service";
    public static final String OCR_SERVICE = "ocr-service";

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slowCallRateThreshold(50)
                .slowCallDurationThreshold(Duration.ofSeconds(20))
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordException(ExternalServiceFailureClassifier.IS_TRANSIENT)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        // Eagerly create both so they're visible/queryable from startup rather than
        // materializing lazily on first call.
        registry.circuitBreaker(FACE_SERVICE);
        registry.circuitBreaker(OCR_SERVICE);
        return registry;
    }
}
