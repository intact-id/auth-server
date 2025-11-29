package com.intact_id.security.validator;

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

//    API KEY (SEND VIA EMAIL): ak_live_dae_nlmexjpqp-xnpkhxrl6-tytfikdm
//⚠️ API SECRET (SEND VIA EMAIL): as_live_t2gcsr4rfrzan5_hq0kcf_7lhmuakapp
    public boolean validateCredentials(HttpServletRequest httpServletRequest) {
        try {
            var headers = getCredentials(httpServletRequest);
            var companyCredentials = companyCredentialService.getByApiKeyCompanyCredentialsWithStatusActive(headers.apiKey);

            return compareAndValidate(companyCredentials.getApiKeyHash(), companyCredentials.getApiSecretHash(), headers);

        } catch (ApplicationExceptions.IntactUnauthorizedException e) {
            throw new ApplicationExceptions.IntactUnauthorizedException(e.getMessage());
        }
    }

    private boolean compareAndValidate(String apiKeyHash, String apiSecretHash, Credentials headers) {
        log.info("{} {}",headers.apiKey ,apiKeyHash);
        log.info("{},{}",headers.apiSecret,apiSecretHash);
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


    @Builder
    private record Credentials(String apiKey, String apiSecret) {
    }
}
