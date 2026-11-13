package com.intact_id.checks;

/**
 * Looks a business up in its country's company registry (e.g. Kenya's Business Registration Service).
 * One implementation is active; the real provider replaces the placeholder in checks.registry.
 */
public interface BusinessRegistry {

    /**
     * @param registered whether the registry has the business
     * @param status     the registry's status for it, e.g. ACTIVE, DEREGISTERED; null if unknown
     * @param source     which registry or provider answered, for the audit trail
     */
    record Lookup(boolean registered, String status, String source) {
    }

    Lookup lookup(String registrationNumber, String companyName, String country);
}
