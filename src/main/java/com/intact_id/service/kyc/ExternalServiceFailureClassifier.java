package com.intact_id.service.kyc;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.function.Predicate;

/**
 * Classifies a face-service/OCR failure as transient (the service's fault — worth holding
 * for retry) vs terminal (bad input data — retrying changes nothing). Shared by the circuit
 * breakers guarding the Feign clients (ResilienceConfig) and VerificationProcessor's failure
 * handling so both agree on what counts as "the service's fault."
 */
public final class ExternalServiceFailureClassifier {

    public static final Predicate<Throwable> IS_TRANSIENT = ExternalServiceFailureClassifier::isTransient;

    private ExternalServiceFailureClassifier() {
    }

    public static boolean isTransient(Throwable cause) {
        if (cause instanceof CallNotPermittedException) return true;
        if (cause instanceof feign.RetryableException) return true;
        if (cause instanceof SocketTimeoutException) return true;
        if (cause instanceof feign.FeignException feignEx) {
            return feignEx.status() >= 500 || feignEx.status() == -1;
        }
        return cause instanceof IOException;
    }
}
