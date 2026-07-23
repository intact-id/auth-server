package com.intact_id.service.apiKey;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.dto.response.ApiKeyGenerationResponseDto;
import com.intact_id.dto.response.ApiKeyUsageResponseDto;
import com.intact_id.service.NotificationService.NotificationService;
import com.intact_id.service.email.EmailService;
import com.intact_id.util.TenantContext;
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
    private final CompanyCommunicationRepository communicationRepository;
    private final GeneratorUtils generatorUtils;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public ApiKeyGenerationResponseDto generateApiKey(String companyId) {
        return generateApiKeyForEnv(companyId, ApiKeyEnvironment.PROD);
    }

    @Override
    @Transactional
    public ApiKeyGenerationResponseDto generateDevApiKey(String companyId) {
        return generateApiKeyForEnv(companyId, ApiKeyEnvironment.DEV);
    }

    @Override
    @Transactional
    public ApiKeyGenerationResponseDto generateProdApiKey(String companyId) {
        return generateApiKeyForEnv(companyId, ApiKeyEnvironment.PROD);
    }

    @Override
    @Transactional
    public void revokeApiKey(String companyId, String apiKeyId) {
        // Note: Currently CompanyCredentials is 1:1 with Company, so apiKeyId is
        // redundant but kept for future extensibility
        // or we can treat companyId as the identifier.
        // For now, we just revoke the credentials for the company.

        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Revoking API key for company: {}", resolvedCompanyId);

        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Credentials not found"));

        credentials.revoke();
        credentials.setDevStatus(GlobalStatuses.REVOKED);
        credentials.setProdStatus(GlobalStatuses.REVOKED);
        credentialsRepository.save(credentials);
    }

    @Override
    public List<ApiKeyGenerationResponseDto> getApiKeys(String companyId) {
        // Since it's 1:1, we return a list with single item or empty
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        return credentialsRepository.findByCompanyId(resolvedCompanyId)
                .map(creds -> {
                    List<ApiKeyGenerationResponseDto> responses = new java.util.ArrayList<>();
                    if (creds.getApiKeyHash(ApiKeyEnvironment.PROD) != null) {
                        responses.add(ApiKeyGenerationResponseDto.builder()
                                .apiKey("****************")
                                .apiSecret("****************")
                                .expiresAt(creds.getExpiresAt(ApiKeyEnvironment.PROD))
                                .status((creds.getProdStatus() != null ? creds.getProdStatus() : creds.getStatus()).name())
                                .environment(ApiKeyEnvironment.PROD.name())
                                .build());
                    }
                    if (creds.getApiKeyHash(ApiKeyEnvironment.DEV) != null) {
                        responses.add(ApiKeyGenerationResponseDto.builder()
                                .apiKey("****************")
                                .apiSecret("****************")
                                .expiresAt(creds.getExpiresAt(ApiKeyEnvironment.DEV))
                                .status((creds.getDevStatus() != null ? creds.getDevStatus() : creds.getStatus()).name())
                                .environment(ApiKeyEnvironment.DEV.name())
                                .build());
                    }
                    return responses;
                })
                .orElse(Collections.emptyList());
    }

    @Override
    public ApiKeyUsageResponseDto getUsage(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        CompanyCredentials creds = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Credentials not found"));

        return ApiKeyUsageResponseDto.builder()
                .companyId(creds.getCompany().getId())
                .companyName(creds.getCompany().getLegalName())
                .devTotalRequests(creds.getDevTotalRequests())
                .devTotalSuccessfulRequests(creds.getDevTotalSuccessfulRequests())
                .devTotalFailedRequests(creds.getDevTotalFailedRequests())
                .devLastUsedAt(creds.getDevLastUsedAt())
                .devRateLimitPerHour(creds.getRateLimitPerHour(ApiKeyEnvironment.DEV))
                .devRateLimitPerDay(creds.getRateLimitPerDay(ApiKeyEnvironment.DEV))
                .devRateLimitPerMonth(creds.getRateLimitPerMonth(ApiKeyEnvironment.DEV))
                .prodTotalRequests(creds.getProdTotalRequests())
                .prodTotalSuccessfulRequests(creds.getProdTotalSuccessfulRequests())
                .prodTotalFailedRequests(creds.getProdTotalFailedRequests())
                .prodLastUsedAt(creds.getProdLastUsedAt())
                .prodRateLimitPerHour(creds.getRateLimitPerHour(ApiKeyEnvironment.PROD))
                .prodRateLimitPerDay(creds.getRateLimitPerDay(ApiKeyEnvironment.PROD))
                .prodRateLimitPerMonth(creds.getRateLimitPerMonth(ApiKeyEnvironment.PROD))
                .build();
    }

    private ApiKeyGenerationResponseDto generateApiKeyForEnv(String companyId, ApiKeyEnvironment environment) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Generating {} API key for company: {}", environment.name(), resolvedCompanyId);

        assert resolvedCompanyId != null;
        Company company = companyRepository.findById(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Company not found"));

        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElse(new CompanyCredentials());

        if (credentials.getCompany() == null) {
            credentials.setCompany(company);
        }

        String rawApiKey;
        String rawApiSecret;
        if (environment == ApiKeyEnvironment.DEV) {
            rawApiKey    = generatorUtils.generateDevApiKey();
            rawApiSecret = generatorUtils.generateDevApiSecret();
        } else {
            rawApiKey    = generatorUtils.generateApiKey();
            rawApiSecret = generatorUtils.generateApiSecret();
        }

        if (environment == ApiKeyEnvironment.DEV) {
            credentials.setDevApiKeyHash(generatorUtils.hashString(rawApiKey));
            credentials.setDevApiSecretHash(generatorUtils.hashString(rawApiSecret));
            credentials.setDevStatus(GlobalStatuses.ACTIVE);
            credentials.setDevExpiresAt(LocalDateTime.now().plusYears(1));
        } else {
            credentials.setProdApiKeyHash(generatorUtils.hashString(rawApiKey));
            credentials.setProdApiSecretHash(generatorUtils.hashString(rawApiSecret));
            credentials.setProdStatus(GlobalStatuses.ACTIVE);
            credentials.setProdExpiresAt(LocalDateTime.now().plusYears(1));

            // Legacy fields for backward compatibility
            credentials.setApiKeyHash(credentials.getProdApiKeyHash());
            credentials.setApiSecretHash(credentials.getProdApiSecretHash());
            credentials.setStatus(credentials.getProdStatus());
            credentials.setExpiresAt(credentials.getProdExpiresAt());
        }

        credentialsRepository.save(credentials);

        ApiKeyGenerationResponseDto response = ApiKeyGenerationResponseDto.builder()
                .apiKey(rawApiKey)
                .apiSecret(rawApiSecret)
                .expiresAt(credentials.getExpiresAt(environment))
                .status((environment == ApiKeyEnvironment.DEV ? credentials.getDevStatus() : credentials.getProdStatus()).name())
                .environment(environment.name())
                .build();

        notifyCompanyOfRegeneratedCredentials(company, response);

        return response;
    }

    private void notifyCompanyOfRegeneratedCredentials(Company company, ApiKeyGenerationResponseDto response) {
        try {
            CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(company.getId())
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Communication details not found for company"));

            boolean emailSent = emailService.sendCompanyCredentialsRegeneratedEmail(
                    communication.getEmail(),
                    company.getLegalName(),
                    response.getApiKey(),
                    response.getApiSecret(),
                    response.getEnvironment());

            if (!emailSent) {
                log.warn("Failed to send regenerated {} API credentials email to company {}",
                        response.getEnvironment(), company.getId());
                return;
            }

            notificationService.persistNotification(
                    company.getId(),
                    response.getEnvironment() + " API Credentials Regenerated",
                    "Your " + response.getEnvironment()
                            + " API credentials were regenerated and sent to your registered company email.",
                    "INFO");
        } catch (Exception e) {
            log.error("Failed to notify company {} about regenerated {} API credentials",
                    company.getId(), response.getEnvironment(), e);
        }
    }
}
