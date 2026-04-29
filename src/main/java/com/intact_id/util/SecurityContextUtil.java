package com.intact_id.util;

import com.intact_id.db.model.Company;
import com.intact_id.db.model.user.User;
import com.intact_id.security.authentication.CompanyAuthentication;
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
            if (isSuperAdminUser(user) && requestedCompanyId != null && !requestedCompanyId.isBlank()) {
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
