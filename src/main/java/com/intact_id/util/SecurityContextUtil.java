package com.intact_id.util;

import com.intact_id.db.model.Company;
import com.intact_id.security.authentication.CompanyAuthentication;
import com.intact_id.util.exception.ApplicationExceptions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
