package com.intact_id.security.filter;

import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.security.authentication.CompanyAuthentication;
import com.intact_id.security.validator.CredentialsValidator;
import com.intact_id.service.credentials.CompanyCredentialService;
import com.intact_id.util.exception.ApplicationExceptions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * API Key Authentication Filter
 * Validates X-API-Key and X-API-Secret headers and sets Company in security
 * context
 */
@Slf4j
@Component
@AllArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final CredentialsValidator credentialsValidator;
    private final CompanyCredentialService companyCredentialService;

    // Paths that don't require API key authentication
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/public/companies/register",
            "/api/public/applications/",
            "/api/auth/",
            "/api/admin/",
            "/api/company-approval/",
            "/api/v1/tiers",
            "/swagger-ui",
            "/webjars",
            "/v3/api-docs",
            "/favicon.ico",
            "/actuator");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip authentication for excluded paths
        if (shouldSkipAuthentication(requestPath)) {
            log.debug("Skipping authentication for path: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Validate credentials
            boolean isValid = credentialsValidator.validateCredentials(request);

            if (!isValid) {
                log.warn("Invalid API credentials for path: {}", requestPath);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API credentials");
                return;
            }

            // Get company from credentials
            String apiKey = request.getHeader("X-API-Key");
            CompanyCredentials credentials = companyCredentialService
                    .getByApiKeyCompanyCredentialsWithStatusActive(apiKey);

            // Set company in security context
            CompanyAuthentication authentication = new CompanyAuthentication(credentials.getCompany());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("Authenticated company: {} for path: {}",
                    credentials.getCompany().getLegalName(), requestPath);

            // TODO: Check rate limits here
            // if (!rateLimitService.checkLimit(credentials.getCompanyId())) {
            // response.sendError(429, "Rate limit exceeded");
            // return;
            // }

            filterChain.doFilter(request, response);

        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.error("Unauthorized attempt: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.error("Company not found: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API credentials");
        } catch (Exception e) {
            log.error("Authentication error", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed");
        } finally {
            // Clear security context after request
            SecurityContextHolder.clearContext();
        }
    }

    private boolean shouldSkipAuthentication(String requestPath) {
        return EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith);
    }
}
