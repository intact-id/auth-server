package com.intact_id.checks.registry;

import com.intact_id.checks.BusinessRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder until the registry provider is connected: every business is reported as registered and active,
 * marked with a source that says so. Replace this class with the provider's implementation.
 */
@Slf4j
@Component
class PendingProviderBusinessRegistry implements BusinessRegistry {

    static final String SOURCE = "pending-provider";

    @Override
    public Lookup lookup(String registrationNumber, String companyName, String country) {
        log.info("Business registry provider not connected; reporting {} ({}) as registered", companyName, registrationNumber);
        return new Lookup(true, "ACTIVE", SOURCE);
    }
}
