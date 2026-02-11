package com.intact_id.util;

import com.intact_id.util.exception.ApplicationExceptions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tenant context helpers for company-scoped access.
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static boolean isAdmin() {
        return SecurityContextUtil.isAdminAuthenticated();
    }

    public static boolean isSuperAdmin() {
        return SecurityContextUtil.isSuperAdminAuthenticated();
    }

    public static String resolveCompanyId(String requestedCompanyId) {
        return SecurityContextUtil.resolveCompanyId(requestedCompanyId);
    }

    /**
     * Resolve company id only when a security context is present.
     * Allows internal service calls without authentication to pass through.
     */
    public static String resolveCompanyIdIfAuthenticated(String requestedCompanyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return requestedCompanyId;
        }
        if (isSuperAdmin()) {
            if (requestedCompanyId != null && !requestedCompanyId.isBlank()) {
                return SecurityContextUtil.resolveCompanyId(requestedCompanyId);
            }
            return null;
        }
        return requireCompanyId();
    }

    public static String requireCompanyId() {
        return SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
    }

    public static void requireAdmin() {
        if (!isSuperAdmin()) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Super admin access required");
        }
    }
}
