package com.intact_id.accounts.credentials;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.Company;
import com.intact_id.notifications.CompanyCommunicationDetails;
import com.intact_id.accounts.CompanyCredentials;
import com.intact_id.notifications.CompanyCommunicationRepository;
import com.intact_id.accounts.CompanyCredentialsRepository;
import com.intact_id.accounts.CompanyRepository;
import com.intact_id.accounts.web.ApiKeyGenerationResponseDto;
import com.intact_id.accounts.web.ApiKeyUsageResponseDto;
import com.intact_id.accounts.NotificationService;
import com.intact_id.notifications.EmailService;
import com.intact_id.accounts.TenantContext;
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

    /** Creates the company's key pair, replacing any previous one. Works only on this server. */
    @Override
    @Transactional
    public ApiKeyGenerationResponseDto generateApiKey(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Generating API key for company: {}", resolvedCompanyId);

        Company company = companyRepository.findById(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Company not found"));
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElse(new CompanyCredentials());
        if (credentials.getCompany() == null) {
            credentials.setCompany(company);
        }

        String rawApiKey = generatorUtils.generateApiKey();
        String rawApiSecret = generatorUtils.generateApiSecret();
        credentials.setApiKeyHash(generatorUtils.hashString(rawApiKey));
        credentials.setApiSecretHash(generatorUtils.hashString(rawApiSecret));
        credentials.setStatus(GlobalStatuses.ACTIVE);
        credentials.setExpiresAt(LocalDateTime.now().plusYears(1));
        credentialsRepository.save(credentials);

        ApiKeyGenerationResponseDto response = ApiKeyGenerationResponseDto.builder()
                .apiKey(rawApiKey)
                .apiSecret(rawApiSecret)
                .expiresAt(credentials.getExpiresAt())
                .status(credentials.getStatus().name())
                .build();
        notifyCompanyOfRegeneratedCredentials(company, response);
        return response;
    }

    @Override
    @Transactional
    public void revokeApiKey(String companyId, String apiKeyId) {
        // Credentials are 1:1 with the company; apiKeyId is kept for the URL shape
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        log.info("Revoking API key for company: {}", resolvedCompanyId);
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException("Credentials not found"));
        credentials.revoke();
        credentialsRepository.save(credentials);
    }

    @Override
    public List<ApiKeyGenerationResponseDto> getApiKeys(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        return credentialsRepository.findByCompanyId(resolvedCompanyId)
                .filter(creds -> creds.getApiKeyHash() != null)
                .map(creds -> List.of(ApiKeyGenerationResponseDto.builder()
                        .apiKey("****************")
                        .apiSecret("****************")
                        .expiresAt(creds.getExpiresAt())
                        .status(creds.getStatus().name())
                        .build()))
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
                .totalRequests(creds.getTotalRequests())
                .totalSuccessfulRequests(creds.getTotalSuccessfulRequests())
                .totalFailedRequests(creds.getTotalFailedRequests())
                .lastUsedAt(creds.getLastUsedAt())
                .rateLimitPerHour(creds.getRateLimitPerHour())
                .rateLimitPerDay(creds.getRateLimitPerDay())
                .rateLimitPerMonth(creds.getRateLimitPerMonth())
                .build();
    }

    private void notifyCompanyOfRegeneratedCredentials(Company company, ApiKeyGenerationResponseDto response) {
        try {
            CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(company.getId())
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Communication details not found for company"));

            boolean emailSent = emailService.sendCompanyCredentialsRegeneratedEmail(
                    communication.getEmail(), company.getLegalName(), response.getApiKey(), response.getApiSecret());
            if (!emailSent) {
                log.warn("Failed to send regenerated API credentials email to company {}", company.getId());
                return;
            }
            notificationService.persistNotification(company.getId(), "API Credentials Regenerated",
                    "Your API credentials were regenerated and sent to your registered company email.", "INFO");
        } catch (Exception e) {
            log.error("Failed to notify company {} about regenerated API credentials", company.getId(), e);
        }
    }
}
