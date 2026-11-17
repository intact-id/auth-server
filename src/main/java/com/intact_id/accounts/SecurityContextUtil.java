package com.intact_id.accounts;

import com.intact_id.util.exception.ApplicationExceptions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for extracting information from Spring Security Context
 */
public class SecurityContextUtil {

    /**
     * Get the authenticated company from security context
     *
     * @return Company object
     * @throws ApplicationExceptions.IntactUnauthorizedException if no company is
     *                                                           authenticated
     */
    public static Company getAuthenticatedCompany() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof CompanyAuthentication) {
            return ((CompanyAuthentication) authentication).getCompany();
        }

        throw new ApplicationExceptions.IntactUnauthorizedException(
                "No authenticated company found in security context");
    }

    /**
     * Get authenticated company from either API key auth or JWT user company
     */
    public static Company getAuthenticatedCompanyFromAny() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "No authentication found in security context");
        }

        if (authentication instanceof CompanyAuthentication) {
            return ((CompanyAuthentication) authentication).getCompany();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            Company company = user.getCompany();
            if (company != null) {
                return company;
            }
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "User '" + user.getUsername() + "' is not assigned to any company. "
                    + "Please contact an administrator to assign a company to your account.");
        }

        throw new ApplicationExceptions.IntactUnauthorizedException(
                "No authenticated company found in security context");
    }

    /**
     * Check if there is an authenticated company in the context
     *
     * @return true if company is authenticated
     */
    public static boolean hasAuthenticatedCompany() {
        try {
            getAuthenticatedCompany();
            return true;
        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            return false;
        }
    }

    /**
     * Get company ID from security context
     *
     * @return Company ID
     */
    public static String getAuthenticatedCompanyId() {
        return getAuthenticatedCompany().getId();
    }

    /**
     * Resolve company id based on auth. If admin user and requestedCompanyId is provided, allow it.
     */
    public static String resolveCompanyId(String requestedCompanyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "No authentication found in security context");
        }

        if (authentication instanceof CompanyAuthentication) {
            return ((CompanyAuthentication) authentication).getCompany().getId();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            if (isStaffUser(user) && requestedCompanyId != null && !requestedCompanyId.isBlank()) {
                return requestedCompanyId;
            }
            Company company = user.getCompany();
            if (company != null) {
                return company.getId();
            }
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "User '" + user.getUsername() + "' is not assigned to any company. "
                    + "Please contact an administrator to assign a company to your account.");
        }

        throw new ApplicationExceptions.IntactUnauthorizedException(
                "No authenticated company found in security context");
    }

    public static boolean isAdminAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return isAdminUser(user);
        }
        return false;
    }

    public static boolean isSuperAdminAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return isSuperAdminUser(user);
        }
        return false;
    }

    /**
     * Intact staff: a signed-in person with no company of their own and a staff role. They may act for any company,
     * as far as their permissions allow (what they may do is always checked separately).
     */
    public static boolean isStaffAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user && isStaffUser(user);
    }

    public static boolean isStaffUser(User user) {
        return user.getCompany() == null && user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> role.getName() != null && Permissions.STAFF_ROLES.contains(role.getName()));
    }

    /** Whether the caller holds this permission (see Permissions). */
    public static boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    public static void requirePermission(String permission) {
        if (!hasPermission(permission)) {
            throw new ApplicationExceptions.IntactForbiddenException("You don't have permission to do this");
        }
    }

    /**
     * Get authenticated user from JWT auth context.
     */
    public static User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApplicationExceptions.IntactUnauthorizedException(
                    "No authentication found in security context");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        throw new ApplicationExceptions.IntactUnauthorizedException(
                "No authenticated user found in security context");
    }

    private static boolean isAdminUser(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        Set<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName() != null ? role.getName().toUpperCase() : "")
            .collect(Collectors.toSet());
        return roleNames.contains("SUPER_ADMIN") || roleNames.contains("ADMIN");
    }

    private static boolean isSuperAdminUser(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        Set<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName() != null ? role.getName().toUpperCase() : "")
            .collect(Collectors.toSet());
        return roleNames.contains("SUPER_ADMIN");
    }
}
