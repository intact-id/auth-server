package com.intact_id.accounts;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every permission the API checks (@PreAuthorize("hasAuthority('area:action')")), the built-in roles that hold them,
 * and what an API key may do. Names are area:view / area:manage; "-platform" permissions are for Intact staff only,
 * "-api" ones for company API keys. Custom roles (made by company admins) may only hold company permissions.
 */
public final class Permissions {

    /** Who a permission can be given to: anyone in a company, Intact staff only, or API keys only. */
    public enum Scope { COMPANY, PLATFORM, API }

    /** A permission as the roles screen shows it. */
    public record Info(String name, String group, String label, Scope scope) {
    }

    private static final List<Info> CATALOGUE = List.of(
            company("verifications:view", "Verifications", "See verifications and their documents"),
            company("verifications:manage", "Verifications", "Release held verifications"),
            company("reviews:view", "Verifications", "See the manual review queue"),
            company("reviews:manage", "Verifications", "Take and decide manual reviews"),
            company("analytics:view", "Verifications", "See statistics and the overview"),
            company("kyb:view", "Business verification", "See directors and owners, and businesses waiting"),
            company("kyb:invite", "Business verification", "Send directors and owners their verification links"),
            company("kyb:manage", "Business verification", "Change KYB settings"),
            company("screening:view", "Screening and risk", "See screening results"),
            company("screening:manage", "Screening and risk", "Run a screening check by hand"),
            company("monitoring:use", "Screening and risk", "See monitoring, decide alerts, start or stop monitoring"),
            company("monitoring:manage", "Screening and risk", "Change monitoring settings"),
            company("risk:manage", "Screening and risk", "Create, change and activate risk models"),
            company("workflows:manage", "Workflows", "Create, change, publish and archive workflows"),
            company("services:available", "Workflows", "See the services workflows can use"),
            company("services:view", "Workflows", "See the full service catalog"),
            company("services:manage", "Workflows", "Price a workflow"),
            company("apikeys:view", "Developers", "See API keys and their usage"),
            company("apikeys:manage", "Developers", "Create and revoke API keys"),
            company("webhooks:view", "Developers", "See webhook settings and deliveries"),
            company("webhooks:manage", "Developers", "Change webhook settings, test and rotate the secret"),
            company("companies:profile", "Team and company", "See the company profile"),
            company("companies:view", "Team and company", "See company details and limits"),
            company("companies:manage", "Team and company", "Change the company profile and credentials"),
            company("users:view", "Team and company", "See team members"),
            company("users:manage", "Team and company", "Invite and change team members"),
            company("roles:manage", "Team and company", "Create roles and assign them to team members"),
            company("documents:view", "Team and company", "See the company's compliance documents"),
            company("documents:manage", "Team and company", "Upload compliance documents"),
            company("notifications:inbox", "Team and company", "See and clear notifications"),
            company("notifications:view", "Team and company", "See all of the company's notifications"),
            company("notifications:manage", "Team and company", "Send and delete notifications"),
            company("topups:view", "Billing", "See wallet top-ups"),
            company("topups:manage", "Billing", "Submit and cancel bank transfer top-ups"),
            company("privacy:view", "Data protection", "See retention rules, legal holds and erasures"),
            company("privacy:requests", "Data protection", "Erase data, see a verification's retention"),
            company("privacy:manage", "Data protection", "Change retention periods and legal holds"),
            company("privacy:manage-company", "Data protection", "Record that a customer relationship ended"),
            platform("applications:view", "Applications", "See company applications"),
            platform("applications:make", "Applications", "Make the first decision on an application"),
            platform("applications:check", "Applications", "Make the final (checker) decision on an application"),
            platform("applications:manage", "Applications", "Delete applications"),
            platform("companies:manage-platform", "Companies", "Change companies' rate limits and notification settings"),
            platform("users:view-platform", "Companies", "See Intact staff and every company's users"),
            platform("users:manage-platform", "Companies", "Suspend users and assign staff roles"),
            platform("kyb:manage-platform", "Companies", "Change the platform's KYB defaults"),
            platform("documents:review-platform", "Companies", "Approve and reject companies' compliance documents"),
            platform("privacy:manage-platform", "Companies", "Change the retention rules per country"),
            platform("privacy:override-platform", "Companies", "Erase data before its minimum period"),
            platform("services:manage-platform", "Catalog and pricing", "Manage the service catalog"),
            platform("templates:manage-platform", "Catalog and pricing", "Create and change workflow templates"),
            platform("billing:view-platform", "Catalog and pricing", "See companies' billing"),
            platform("billing:manage", "Catalog and pricing", "Change companies' billing (older billing pages)"),
            platform("billing:manage-platform", "Catalog and pricing", "Set up billing, prices, FX, credits and invoices"),
            platform("topups:manage-platform", "Catalog and pricing", "Approve and reject top-ups"),
            platform("analytics:view-platform", "Platform", "See verification timelines and turnaround"),
            platform("audit:view", "Platform", "See the audit log"),
            platform("system:use", "Platform", "See system status"),
            api("widget:use", "Widget", "Widget sessions"),
            api("widget:view", "Widget", "Widget verification status"),
            api("widget:manage", "Widget", "Widget drafts and handoffs"),
            api("verifications:manage-api", "API", "Submit verifications"),
            api("kyb:manage-api", "API", "Submit business verifications"),
            api("kyb:view-api", "API", "Read business verifications from the widget"),
            api("billing:view-api", "API", "Read the company's billing"),
            api("apikeys:manage-api", "API", "Exchange the key for an access token"));

    public static final Set<String> ALL = names(CATALOGUE.stream());

    /** Permissions a company role (built-in or custom) may hold. */
    public static final Set<String> COMPANY_GRANTABLE = names(CATALOGUE.stream().filter(i -> i.scope() == Scope.COMPANY));

    /** A company administrator: everything for their own company. */
    public static final Set<String> COMPANY_ADMIN = COMPANY_GRANTABLE;

    private static final Set<String> COMPLIANCE_OFFICER = Set.of(
            "analytics:view", "verifications:view", "verifications:manage", "reviews:view", "reviews:manage",
            "kyb:view", "kyb:invite", "kyb:manage", "screening:view", "screening:manage", "monitoring:use",
            "monitoring:manage", "risk:manage", "privacy:view", "privacy:requests", "privacy:manage",
            "privacy:manage-company", "notifications:inbox", "notifications:view", "services:available", "services:view",
            "companies:profile", "documents:view", "documents:manage");

    private static final Set<String> REVIEWER = Set.of(
            "analytics:view", "verifications:view", "reviews:view", "reviews:manage", "kyb:view", "kyb:invite",
            "monitoring:use", "screening:view", "privacy:view", "notifications:inbox", "services:available",
            "companies:profile");

    private static final Set<String> DEVELOPER = Set.of(
            "analytics:view", "verifications:view", "kyb:view", "apikeys:view", "apikeys:manage", "webhooks:view",
            "webhooks:manage", "workflows:manage", "services:view", "services:available", "services:manage",
            "notifications:inbox", "companies:profile");

    private static final Set<String> FINANCE = Set.of(
            "analytics:view", "topups:view", "topups:manage", "notifications:inbox", "services:available",
            "companies:profile");

    private static final Set<String> VIEWER = Set.of(
            "analytics:view", "verifications:view", "reviews:view", "kyb:view", "screening:view", "privacy:view",
            "notifications:inbox", "services:available", "services:view", "webhooks:view", "apikeys:view",
            "topups:view", "companies:profile", "documents:view");

    /** An Intact super admin: every company and staff permission (not the API-key-only ones). */
    public static final Set<String> SUPER_ADMIN = Stream.concat(COMPANY_GRANTABLE.stream(),
                    CATALOGUE.stream().filter(i -> i.scope() == Scope.PLATFORM).map(Info::name))
            // Recorded by the company itself, as before
            .filter(p -> !p.equals("privacy:manage-company"))
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> OPERATIONS = Set.of(
            "applications:view", "applications:make", "companies:view", "companies:manage", "companies:manage-platform",
            "companies:profile", "webhooks:view", "users:view", "users:view-platform", "users:manage",
            "verifications:view", "verifications:manage", "reviews:view", "reviews:manage", "kyb:view", "kyb:invite",
            "kyb:manage", "kyb:manage-platform", "analytics:view", "analytics:view-platform", "notifications:inbox",
            "notifications:view", "notifications:manage", "monitoring:use", "screening:view", "services:view",
            "services:available", "privacy:view", "system:use", "topups:view", "documents:view", "documents:review-platform");

    private static final Set<String> STAFF_FINANCE = Set.of(
            "billing:manage", "billing:view-platform", "billing:manage-platform", "topups:view", "topups:manage-platform",
            "companies:view", "companies:profile", "analytics:view", "analytics:view-platform", "notifications:inbox",
            "services:view", "services:available");

    /** Read-only across every company. */
    private static final Set<String> SUPPORT = SUPER_ADMIN.stream()
            .filter(p -> p.endsWith(":view") || p.endsWith(":view-platform") || p.equals("audit:view")
                    || p.equals("system:use") || p.equals("companies:profile") || p.equals("notifications:inbox")
                    || p.equals("services:available"))
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> MAKER = Set.of("applications:view", "applications:make", "companies:view", "notifications:inbox", "documents:view");

    private static final Set<String> CHECKER = Set.of("applications:view", "applications:check", "companies:view", "notifications:inbox", "documents:view");

    /** A built-in role: its name in the database, how the console shows it, who holds it, and what it may do. */
    public record BuiltInRole(String name, String label, String description, boolean staff, Set<String> permissions) {
    }

    public static final List<BuiltInRole> BUILT_IN_ROLES = List.of(
            new BuiltInRole("ADMIN", "Admin", "Everything for the company, including team and roles", false, COMPANY_ADMIN),
            new BuiltInRole("COMPLIANCE_OFFICER", "Compliance officer",
                    "Verifications, reviews, monitoring, screening, risk models and data protection", false, COMPLIANCE_OFFICER),
            new BuiltInRole("REVIEWER", "Reviewer", "Sees verifications; decides manual reviews and monitoring alerts", false, REVIEWER),
            new BuiltInRole("DEVELOPER", "Developer", "API keys, webhooks and workflows", false, DEVELOPER),
            new BuiltInRole("FINANCE", "Finance", "Billing, invoices and top-ups", false, FINANCE),
            new BuiltInRole("VIEWER", "Viewer", "Read-only", false, VIEWER),
            new BuiltInRole("SUPER_ADMIN", "Super admin", "Everything across the platform", true, SUPER_ADMIN),
            new BuiltInRole("OPERATIONS", "Operations", "Applications, companies, their users and reviews", true, OPERATIONS),
            new BuiltInRole("STAFF_FINANCE", "Finance (Intact)", "Prices, billing set-up, FX and top-up reviews", true, STAFF_FINANCE),
            new BuiltInRole("SUPPORT", "Support", "Read-only across every company", true, SUPPORT),
            new BuiltInRole("MAKER", "Application maker", "First decision on company applications", true, MAKER),
            new BuiltInRole("CHECKER", "Application checker", "Final decision on company applications", true, CHECKER));

    private static final Map<String, BuiltInRole> BY_NAME = BUILT_IN_ROLES.stream()
            .collect(Collectors.toMap(BuiltInRole::name, r -> r, (a, b) -> a, LinkedHashMap::new));

    /** Roles that make a user Intact staff: no company of their own, acting across companies as their permissions allow. */
    public static final Set<String> STAFF_ROLES = BUILT_IN_ROLES.stream().filter(BuiltInRole::staff)
            .map(BuiltInRole::name).collect(Collectors.toUnmodifiableSet());

    /** How much a company API key may do. */
    public enum ApiAccess {
        /** Everything an API key can do */
        FULL,
        /** Start sessions, submit verifications and read their results */
        VERIFY,
        /** Read only */
        READ_ONLY
    }

    /** Everything a full-access API key may do (and every widget session). */
    public static final Set<String> API_KEY = Set.of(
            "analytics:view", "apikeys:manage-api", "billing:view-api", "companies:profile", "kyb:invite", "kyb:manage-api",
            "kyb:view", "kyb:view-api", "monitoring:use", "notifications:inbox", "privacy:manage-company",
            "privacy:requests", "privacy:view", "risk:manage", "screening:view", "services:available", "services:manage",
            "verifications:manage-api", "verifications:view", "webhooks:view", "widget:manage", "widget:use",
            "widget:view", "workflows:manage");

    private static final Set<String> API_VERIFY = Set.of(
            "apikeys:manage-api", "widget:manage", "widget:use", "widget:view", "verifications:manage-api",
            "verifications:view", "kyb:manage-api", "kyb:view", "kyb:view-api", "kyb:invite", "services:available",
            "companies:profile");

    private static final Set<String> API_READ_ONLY = Set.of(
            "apikeys:manage-api", "analytics:view", "billing:view-api", "companies:profile", "kyb:view", "kyb:view-api",
            "privacy:view", "screening:view", "services:available", "verifications:view", "webhooks:view");

    public static Set<String> forApiKey(ApiAccess access) {
        return switch (access == null ? ApiAccess.FULL : access) {
            case FULL -> API_KEY;
            case VERIFY -> API_VERIFY;
            case READ_ONLY -> API_READ_ONLY;
        };
    }

    private Permissions() {
    }

    public static List<Info> catalogue() {
        return CATALOGUE;
    }

    public static boolean isBuiltIn(String roleName) {
        return roleName != null && BY_NAME.containsKey(roleName);
    }

    public static BuiltInRole builtIn(String roleName) {
        return BY_NAME.get(roleName);
    }

    /** One role's permissions: a built-in role's from this class, a custom role's as stored (company ones only). */
    public static Set<String> of(Role role) {
        if (role == null || role.getName() == null) return Set.of();
        BuiltInRole builtIn = BY_NAME.get(role.getName());
        if (builtIn != null) return builtIn.permissions();
        return role.getPermissions().stream().map(Permission::getName).filter(COMPANY_GRANTABLE::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The permissions of these roles together. */
    public static Set<String> forRoles(Collection<Role> roles) {
        Set<String> permissions = new LinkedHashSet<>();
        roles.stream().filter(Objects::nonNull).forEach(role -> permissions.addAll(of(role)));
        return permissions;
    }

    public static List<String> sorted(Collection<String> permissions) {
        return permissions.stream().sorted().toList();
    }

    private static Info company(String name, String group, String label) {
        return new Info(name, group, label, Scope.COMPANY);
    }

    private static Info platform(String name, String group, String label) {
        return new Info(name, group, label, Scope.PLATFORM);
    }

    private static Info api(String name, String group, String label) {
        return new Info(name, group, label, Scope.API);
    }

    private static Set<String> names(Stream<Info> infos) {
        return infos.map(Info::name).collect(Collectors.toUnmodifiableSet());
    }
}
