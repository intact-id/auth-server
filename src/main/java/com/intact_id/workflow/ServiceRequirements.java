package com.intact_id.workflow;

import java.util.List;

/**
 * What a set of services needs at runtime.
 *
 * @param services       the services in execution order (dependencies first)
 * @param requiredFields applicant fields to collect, in first-needed order
 * @param captures       uploads to collect
 * @param screens        widget screens to show, in order
 * @param problems       why this set can't run as-is (unknown, unavailable, missing dependency, ...); empty when valid
 */
public record ServiceRequirements(
        List<String> services,
        List<String> requiredFields,
        List<String> captures,
        List<String> screens,
        List<String> problems) {

    public boolean valid() {
        return problems.isEmpty();
    }
}
