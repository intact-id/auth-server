package com.intact_id.service.credentials;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 23/11/2025 14:20
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCredentialsServiceImpl implements CompanyCredentialService {
    private final CompanyCredentialsRepository credentialsRepository;
    private final GeneratorUtils generatorUtils;

    @Override
    public CompanyCredentials getByApiKeyCompanyCredentials(String apiKey, ApiKeyEnvironment environment) {
        var hashedApiKey = generatorUtils.hashString(apiKey);
        Optional<CompanyCredentials> response;
        if (environment == ApiKeyEnvironment.DEV) {
            response = credentialsRepository.findCompanyCredentialsByDevApiKeyHash(hashedApiKey);
        } else {
            response = credentialsRepository.findCompanyCredentialsByProdApiKeyHash(hashedApiKey);
            if (response.isEmpty()) {
                response = credentialsRepository.findCompanyCredentialsByApiKeyHash(hashedApiKey);
            }
        }
        return response.orElseThrow(
                () -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey));
    }

    @Override
    public CompanyCredentials getByApiKeyCompanyCredentialsWithStatusActive(String apiKey, ApiKeyEnvironment environment) {
        var credentials = getByApiKeyCompanyCredentials(apiKey, environment);
        if (!credentials.isActive(environment)) {
            throw new ApplicationExceptions.IntactUnauthorizedException("credentials not active " + apiKey);
        }
        return credentials;

    }

    @Override
    public void updateStatus(String apiKey, ApiKeyEnvironment environment, GlobalStatuses globalStatuses) {
        var hashedApiKey = generatorUtils.hashString(apiKey);
        CompanyCredentials credentials;
        if (environment == ApiKeyEnvironment.DEV) {
            credentials = credentialsRepository.findCompanyCredentialsByDevApiKeyHash(hashedApiKey)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey));
            credentials.setDevStatus(globalStatuses);
        } else {
            credentials = credentialsRepository.findCompanyCredentialsByProdApiKeyHash(hashedApiKey)
                    .orElseGet(() -> credentialsRepository.findCompanyCredentialsByApiKeyHash(hashedApiKey)
                            .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey)));
            credentials.setProdStatus(globalStatuses);
            credentials.setStatus(globalStatuses);
        }
        credentialsRepository.save(credentials);
    }

    @Override
    @Async
    @Transactional
    public void recordUsageAsync(String credentialsId, ApiKeyEnvironment environment, boolean success) {
        credentialsRepository.findById(credentialsId).ifPresent(credentials -> {
            credentials.recordUsage(environment, success);
            credentialsRepository.save(credentials);
        });
    }

    @Override
    @Transactional
    public Map<String, String> generateProdCredentials(String companyId) {
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "No credentials record found for company: " + companyId));

        if (credentials.getProdApiKeyHash() != null) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "Production credentials already exist. Revoke existing keys before generating new ones.");
        }

        String apiKey    = generatorUtils.generateApiKey();
        String apiSecret = generatorUtils.generateApiSecret();

        credentials.setProdApiKeyHash(generatorUtils.hashString(apiKey));
        credentials.setProdApiSecretHash(generatorUtils.hashString(apiSecret));
        credentials.setProdStatus(GlobalStatuses.ACTIVE);
        credentials.setProdRateLimitPerHour(500);
        credentials.setProdRateLimitPerDay(10000);
        credentials.setProdTotalRequests(0L);
        credentials.setProdTotalSuccessfulRequests(0L);
        credentials.setProdTotalFailedRequests(0L);

        credentialsRepository.save(credentials);
        log.info("PROD credentials generated for company: {}", companyId);

        // Return plaintext keys — shown once; caller must display immediately
        return Map.of("apiKey", apiKey, "apiSecret", apiSecret);
    }
}




