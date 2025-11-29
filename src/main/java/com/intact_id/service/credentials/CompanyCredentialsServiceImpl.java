package com.intact_id.service.credentials;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        var hashedApiKey = generatorUtils.hashString(apiKey);
        var response = Optional.ofNullable(credentialsRepository.findCompanyCredentialsByApiKeyHash(hashedApiKey)).orElseThrow(
                () -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey));
        return response.get();
    }

    @Override
    public CompanyCredentials getByApiKeyCompanyCredentialsWithStatusActive(String apiKey) {
        //get the company
        var hashedApiKey = generatorUtils.hashString(apiKey);
        return Optional.ofNullable(credentialsRepository.findCompanyCredentialsByApiKeyHash(hashedApiKey)).orElseThrow(
                () -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey)
        ).filter(CompanyCredentials::isActive).orElseThrow(
                () -> new ApplicationExceptions.IntactUnauthorizedException(" credentials not active  " + apiKey)
        );

    }

    @Override
    public void updateStatus(String apiKey, GlobalStatuses globalStatuses) {
        var hashedApiKey = generatorUtils.hashString(apiKey);
        var credentials = credentialsRepository.findCompanyCredentialsByApiKeyHash(hashedApiKey)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("No credentials found for apiKey " + apiKey));

        credentials.setStatus(globalStatuses);
        credentialsRepository.save(credentials);
    }
}






