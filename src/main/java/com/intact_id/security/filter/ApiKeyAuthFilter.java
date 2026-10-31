package com.intact_id.security.filter;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.security.authentication.CompanyAuthentication;
import com.intact_id.security.validator.CredentialsValidator;
import com.intact_id.service.credentials.CompanyCredentialService;
import com.intact_id.util.exception.ApplicationExceptions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
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

    // Deprecated as of the client-credentials token flow (POST /api/v1/auth/token): key+secret
    // still works here during the migration window, but callers should exchange it for a
    // short-lived access token once and use CompanyAccessTokenAuthFilter instead — avoids the
    // per-request DB lookup + secure hash comparison this filter does on every single call.
    private static final List<String> DEPRECATED_KEY_SECRET_PATHS = Arrays.asList(
            "/api/v1/kyc/verify/",
            "/api/v1/kyc/dev/verify/");

    // Paths that don't require API key authentication
    private static final List<String> EXCLUDED_PATHS = Arrays.asList(
            "/api/public/companies/register",
            "/api/public/applications/",
            "/api/auth/",
            "/api/admin/",
            "/api/company-approval/",
            "/api/v1/tiers",
            "/api/v1/kyc/widget/",
            "/api/v1/kyc/handoff/",
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

        // Skip if already authenticated (e.g., via JWT)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            log.debug("Skipping API key auth - already authenticated via JWT");
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
            ApiKeyEnvironment environment = credentialsValidator.resolveEnvironment(request);
            CompanyCredentials credentials = companyCredentialService
                    .getByApiKeyCompanyCredentialsWithStatusActive(apiKey, environment);

            // Set company in security context
            CompanyAuthentication authentication = new CompanyAuthentication(credentials.getCompany());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.info("Authenticated company: {} for path: {}",
                    credentials.getCompany().getLegalName(), requestPath);

            if (isDeprecatedKeySecretPath(requestPath)) {
                log.warn("Deprecated key/secret auth used on {} by company {} — migrate to POST /api/v1/auth/token",
                        requestPath, credentials.getCompany().getId());
            }

            StatusCaptureResponse wrappedResponse = new StatusCaptureResponse(response);
            boolean success = false;
            try {
                filterChain.doFilter(request, wrappedResponse);
                success = wrappedResponse.getStatus() < 400;
            } catch (Exception e) {
                success = false;
                throw e;
            } finally {
                companyCredentialService.recordUsageAsync(credentials.getId(), environment, success);
            }

        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            log.error("Unauthorized attempt: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.error("Company not found: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API credentials");
        } catch (Exception e) {
            log.error("Authentication error", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed");
        }
    }

    private boolean shouldSkipAuthentication(String requestPath) {
        return EXCLUDED_PATHS.stream().anyMatch(requestPath::startsWith);
    }

    private boolean isDeprecatedKeySecretPath(String requestPath) {
        return DEPRECATED_KEY_SECRET_PATHS.stream().anyMatch(requestPath::contains);
    }

    private static class StatusCaptureResponse extends HttpServletResponseWrapper {
        private int status = HttpServletResponse.SC_OK;

        StatusCaptureResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.status = sc;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            super.sendRedirect(location);
            this.status = HttpServletResponse.SC_FOUND;
        }

        @Override
        public int getStatus() {
            return status;
        }
    }
}
