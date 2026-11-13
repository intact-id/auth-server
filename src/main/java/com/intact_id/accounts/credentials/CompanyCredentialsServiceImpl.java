package com.intact_id.accounts.credentials;


import com.intact_id.accounts.CompanyCredentialService;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.CompanyCredentials;
import com.intact_id.accounts.CompanyCredentialsRepository;
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
    public CompanyCredentials getByApiKeyCompanyCredentials(String apiKey) {
        return credentialsRepository.findCompanyCredentialsByApiKeyHash(generatorUtils.hashString(apiKey))
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey));
    }

    @Override
    public CompanyCredentials getByApiKeyCompanyCredentialsWithStatusActive(String apiKey) {
        var credentials = getByApiKeyCompanyCredentials(apiKey);
        if (!credentials.isActive()) {
            throw new ApplicationExceptions.IntactUnauthorizedException("credentials not active " + apiKey);
        }
        return credentials;
    }

    @Override
    public void updateStatus(String apiKey, GlobalStatuses globalStatuses) {
        var credentials = getByApiKeyCompanyCredentials(apiKey);
        credentials.setStatus(globalStatuses);
        credentialsRepository.save(credentials);
    }

    @Override
    @Async
    @Transactional
    public void recordUsageAsync(String credentialsId, boolean success) {
        credentialsRepository.findById(credentialsId).ifPresent(credentials -> {
            credentials.recordUsage(success);
            credentialsRepository.save(credentials);
        });
    }
}
