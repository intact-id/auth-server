package com.intact_id.security.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.db.model.Company;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.security.authentication.CompanyAuthentication;
import com.intact_id.service.jwt.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the client-credentials access token issued by POST /api/v1/auth/token.
 *
 * Stateless and non-redundant by design: signature + expiry are checked once, in memory, via
 * JwtService (no DB hit); the jti denylist check and the company lookup are both Caffeine
 * cache reads. The resolved Company is attached to the security context here and nothing
 * downstream re-parses or re-validates the token — controllers read it the same way they
 * already read ApiKeyAuthFilter's CompanyAuthentication (SecurityContextUtil.
 * getAuthenticatedCompanyFromAny()), so no controller changes were needed to support this.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyAccessTokenAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CompanyRepository companyRepository;
    private final Cache<String, Long> companyAccessTokenDenylist;
    private final Cache<String, Company> companyAccessTokenCompanyCache;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Skip if already authenticated upstream (admin JWT via JwtAuthenticationFilter, or
        // the iframe/widget session token via KycWidgetAuthFilter).
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        boolean isOurs;
        try {
            isOurs = jwtService.isCompanyAccessToken(token);
        } catch (JwtException e) {
            // Not unambiguously ours to reject — could be a malformed/expired admin JWT on
            // the same shared Authorization header. Pass through rather than guess, matching
            // JwtAuthenticationFilter's own behavior for this header.
            filterChain.doFilter(request, response);
            return;
        }

        if (!isOurs) {
            filterChain.doFilter(request, response);
            return;
        }

        String jti = jwtService.extractJti(token);
        if (jti != null && companyAccessTokenDenylist.getIfPresent(jti) != null) {
            log.warn("Rejected revoked company access token jti={}", jti);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
            return;
        }

        String path = request.getRequestURI();
        ApiKeyEnvironment tokenEnv = ApiKeyEnvironment.valueOf(jwtService.extractCompanyTokenEnvironment(token));
        if (path.contains("/api/v1/kyc/dev/") && tokenEnv != ApiKeyEnvironment.DEV) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token was not issued for the DEV environment");
            return;
        }
        if (path.startsWith("/api/v1/kyc/verify") && tokenEnv != ApiKeyEnvironment.PROD) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token was not issued for the PROD environment");
            return;
        }

        String companyId = jwtService.extractCompanyId(token);
        Company company = companyAccessTokenCompanyCache.get(companyId,
                id -> companyRepository.findById(id).orElse(null));

        if (company == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unknown company");
            return;
        }

        CompanyAuthentication authentication = new CompanyAuthentication(company);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Authenticated company {} via access token for path: {}", company.getId(), path);

        filterChain.doFilter(request, response);
    }
}
