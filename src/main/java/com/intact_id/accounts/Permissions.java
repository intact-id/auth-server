package com.intact_id.accounts;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every permission the API checks (@PreAuthorize("hasAuthority('area:action')")) and which built-in role holds it.
 * Names are area:view / area:manage; "-platform" ones are for Intact staff only, "-api" ones for company API keys.
 * Super admins hold all of them. A roles-and-permissions screen will add custom roles on top of these.
 */
public final class Permissions {

    /** All permissions the API checks. */
    public static final Set<String> ALL = Set.of(
            "analytics:view", "analytics:view-platform", "apikeys:manage", "apikeys:manage-api", "apikeys:view",
            "applications:manage", "applications:view", "audit:view", "billing:manage", "billing:view-api",
            "billing:view-platform", "companies:manage", "companies:manage-platform", "companies:view", "kyb:manage",
            "kyb:manage-api", "kyb:manage-platform", "kyb:view", "kyb:view-api", "monitoring:manage",
            "monitoring:use", "notifications:manage", "notifications:view", "privacy:manage",
            "privacy:manage-company", "privacy:manage-platform", "privacy:view", "reviews:manage", "reviews:view",
            "risk:manage", "screening:manage", "screening:view", "services:manage", "services:manage-platform",
            "services:view", "system:use", "topups:manage", "topups:manage-platform", "topups:view", "users:manage",
            "users:manage-platform", "users:view", "users:view-platform", "verifications:manage",
            "verifications:manage-api", "verifications:view", "widget:manage", "widget:use", "widget:view",
            "workflows:manage");

    /** A company administrator: everything for their own company. */
    public static final Set<String> COMPANY_ADMIN = Set.of(
            "analytics:view", "apikeys:manage", "apikeys:view", "companies:manage", "companies:view", "kyb:manage",
            "kyb:view", "monitoring:manage", "monitoring:use", "notifications:manage", "notifications:view",
            "privacy:manage", "privacy:manage-company", "privacy:view", "reviews:manage", "reviews:view",
            "risk:manage", "screening:manage", "screening:view", "services:manage", "services:view", "topups:manage",
            "topups:view", "users:manage", "users:view", "verifications:manage", "verifications:view",
            "workflows:manage");

    /** A company's API key (server-to-server calls and widget sessions). */
    public static final Set<String> API_KEY = Set.of(
            "analytics:view", "apikeys:manage-api", "billing:view-api", "companies:view", "kyb:manage",
            "kyb:manage-api", "kyb:view", "kyb:view-api", "monitoring:use", "notifications:manage",
            "notifications:view", "privacy:manage", "privacy:manage-company", "privacy:view", "risk:manage",
            "screening:view", "services:manage", "services:view", "verifications:manage-api", "verifications:view",
            "widget:manage", "widget:use", "widget:view", "workflows:manage");

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "SUPER_ADMIN", ALL,
            "ADMIN", COMPANY_ADMIN,
            // Reserved roles, not yet given anything beyond what every signed-in person may do
            "MAKER", Set.of(),
            "CHECKER", Set.of(),
            "VIEWER", Set.of());

    private Permissions() {
    }

    /** The permissions of these built-in roles together; unknown roles add nothing. */
    public static Set<String> forRoles(Collection<String> roleNames) {
        Set<String> permissions = new LinkedHashSet<>();
        roleNames.stream().filter(java.util.Objects::nonNull)
                .forEach(role -> permissions.addAll(BY_ROLE.getOrDefault(role, Set.of())));
        return permissions;
    }

    public static List<String> sorted(Collection<String> permissions) {
        return permissions.stream().sorted().toList();
    }
}
