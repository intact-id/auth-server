package com.intact_id.security.validator;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.service.credentials.CompanyCredentialService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 23/11/2025 14:44
 */

@Slf4j
@Component
@AllArgsConstructor
public class CredentialsValidator {
    private final CompanyCredentialService companyCredentialService;
    private final GeneratorUtils generatorUtils;

    public boolean validateCredentials(HttpServletRequest httpServletRequest) {
        try {
            ApiKeyEnvironment environment = resolveEnvironment(httpServletRequest);
            var headers = getCredentials(httpServletRequest);
            var companyCredentials = companyCredentialService
                    .getByApiKeyCompanyCredentialsWithStatusActive(headers.apiKey, environment);

            return compareAndValidate(
                    companyCredentials.getApiKeyHash(environment),
                    companyCredentials.getApiSecretHash(environment),
                    headers);

        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            throw new ApplicationExceptions.IntactUnauthorizedException(e.getMessage());
        }
    }

    private boolean compareAndValidate(String apiKeyHash, String apiSecretHash, Credentials headers) {
        if (apiKeyHash == null || apiSecretHash == null) {
            return false;
        }
        boolean hashedRequestApikey = generatorUtils.validateHash(headers.apiKey, apiKeyHash);
        boolean hashedRequestApiSecret = generatorUtils.validateHash(headers.apiSecret, apiSecretHash);
        return hashedRequestApikey && hashedRequestApiSecret;
    }


    private Credentials getCredentials(HttpServletRequest httpServletRequest) {
        if (httpServletRequest.getHeader("X-API-Key") != null && httpServletRequest.getHeader("X-API-Secret") != null) {
            return Credentials.builder()
                    .apiKey(httpServletRequest.getHeader("X-API-Key"))
                    .apiSecret(httpServletRequest.getHeader("X-API-Secret"))
                    .build();
        } else
            throw new ApplicationExceptions.IntactUnauthorizedException("no credentials provided");
    }

    public ApiKeyEnvironment resolveEnvironment(HttpServletRequest httpServletRequest) {
        // Resolve from the API key prefix first — most reliable signal
        String apiKey = httpServletRequest.getHeader("X-API-Key");
        if (apiKey != null && apiKey.startsWith("ak_dev_")) {
            return ApiKeyEnvironment.DEV;
        }

        // Fall back to path-based resolution
        String path = httpServletRequest.getRequestURI();
        if (path != null && path.contains("/api/v1/kyc/dev")) {
            return ApiKeyEnvironment.DEV;
        }
        if (path != null && path.contains("/api/v1/kyc/prod")) {
            return ApiKeyEnvironment.PROD;
        }
        return ApiKeyEnvironment.PROD;
    }


    @Builder
    private record Credentials(String apiKey, String apiSecret) {
    }
}
