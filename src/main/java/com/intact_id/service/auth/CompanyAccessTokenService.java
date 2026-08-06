package com.intact_id.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.dto.auth.CompanyAccessTokenResponseDto;
import com.intact_id.security.validator.CredentialsValidator;
import com.intact_id.service.credentials.CompanyCredentialService;
import com.intact_id.service.jwt.JwtService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.security.RequestHeaderUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Client-credentials token exchange for the resource server. The expensive part of
 * authenticating a company — a DB lookup plus a secure hash comparison of the API key and
 * secret (see CredentialsValidator) — happens HERE, once, in exchange for a short-lived
 * token. Everything downstream (CompanyAccessTokenAuthFilter) validates that token with an
 * in-memory signature/expiry check instead of repeating the DB hit + hash comparison.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyAccessTokenService {

    private final CredentialsValidator credentialsValidator;
    private final CompanyCredentialService companyCredentialService;
    private final JwtService jwtService;
    private final TokenIssuanceRateLimiter rateLimiter;
    private final RequestHeaderUtil requestHeaderUtil;
    private final Cache<String, Long> companyAccessTokenDenylist;

    public CompanyAccessTokenResponseDto issueToken(HttpServletRequest request) {
        String apiKey = requestHeaderUtil.getHeader(request, "X-API-Key");
        String clientIp = requestHeaderUtil.getClientIp(request);

        if (!rateLimiter.tryConsume(apiKey, clientIp)) {
            throw new ApplicationExceptions.IntactTooManyRequestsException(
                    "Too many token requests — try again shortly");
        }

        boolean isValid = credentialsValidator.validateCredentials(request);
        if (!isValid) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Invalid API credentials");
        }

        ApiKeyEnvironment environment = credentialsValidator.resolveEnvironment(request);
        CompanyCredentials credentials = companyCredentialService
                .getByApiKeyCompanyCredentialsWithStatusActive(apiKey, environment);
        Company company = credentials.getCompany();

        String accessToken = jwtService.generateCompanyAccessToken(company.getId(), environment);

        log.info("Issued company access token for company={} env={}", company.getId(), environment);

        return CompanyAccessTokenResponseDto.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getCompanyAccessTokenExpiration() / 1000)
                .scope("kyc:verify")
                .build();
    }

    /**
     * Revoke the presented access token before its natural expiry (e.g. on suspected
     * compromise). Adds its jti to the denylist; the token's signature is verified first so a
     * caller can only revoke a token they actually hold, not an arbitrary jti.
     */
    public void revokeToken(String bearerToken) {
        try {
            if (!jwtService.isCompanyAccessToken(bearerToken)) {
                throw new ApplicationExceptions.IntactBadRequestException("Not a company access token");
            }
            String jti = jwtService.extractJti(bearerToken);
            companyAccessTokenDenylist.put(jti, System.currentTimeMillis());
            log.info("Revoked company access token jti={}", jti);
        } catch (JwtException e) {
            throw new ApplicationExceptions.IntactBadRequestException("Invalid or expired token");
        }
    }
}
