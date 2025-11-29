package com.intact_id.service.apiKey;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.dto.response.ApiKeyGenerationResponseDto;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final CompanyCredentialsRepository credentialsRepository;
    private final CompanyRepository companyRepository;
    private final GeneratorUtils generatorUtils;

    @Override
    @Transactional
    public ApiKeyGenerationResponseDto generateApiKey(String companyId) {
        log.info("Generating API key for company: {}", companyId);

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Company not found"));

        // Check if credentials already exist
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(companyId)
                .orElse(new CompanyCredentials());

        if (credentials.getCompany() == null) {
            credentials.setCompany(company);
        }

        // Generate new keys
        String rawApiKey = generatorUtils.generateApiKey();
        String rawApiSecret = generatorUtils.generateApiSecret();

        // Hash and save
        credentials.setApiKeyHash(generatorUtils.hashString(rawApiKey));
        credentials.setApiSecretHash(generatorUtils.hashString(rawApiSecret));
        credentials.setStatus(GlobalStatuses.ACTIVE);
        credentials.setExpiresAt(LocalDateTime.now().plusYears(1)); // Default 1 year expiry

        credentialsRepository.save(credentials);

        return ApiKeyGenerationResponseDto.builder()
                .apiKey(rawApiKey)
                .apiSecret(rawApiSecret)
                .expiresAt(credentials.getExpiresAt())
                .status(credentials.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public void revokeApiKey(String companyId, String apiKeyId) {
        // Note: Currently CompanyCredentials is 1:1 with Company, so apiKeyId is
        // redundant but kept for future extensibility
        // or we can treat companyId as the identifier.
        // For now, we just revoke the credentials for the company.

        log.info("Revoking API key for company: {}", companyId);

        CompanyCredentials credentials = credentialsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Credentials not found"));

        credentials.revoke();
        credentialsRepository.save(credentials);
    }

    @Override
    public List<ApiKeyGenerationResponseDto> getApiKeys(String companyId) {
        // Since it's 1:1, we return a list with single item or empty
        return credentialsRepository.findByCompanyId(companyId)
                .map(creds -> ApiKeyGenerationResponseDto.builder()
                        .apiKey("****************") // Masked
                        .apiSecret("****************") // Masked
                        .expiresAt(creds.getExpiresAt())
                        .status(creds.getStatus().name())
                        .build())
                .map(List::of)
                .orElse(Collections.emptyList());
    }
}
