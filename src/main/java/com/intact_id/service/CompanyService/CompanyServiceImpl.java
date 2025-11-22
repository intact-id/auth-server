package com.intact_id.service.CompanyService;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:07
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyCommunicationRepository communicationRepository;
    private final CompanyCredentialsRepository credentialsRepository;
    private final GeneratorUtils generatorUtils;

    @Transactional
    public Company createCompanyFromApplication(CompanyApplication application) {
        log.info("Creating company from application: {}", application.getApplicationNumber());

        // 1. Create Company
        Company company = new Company();
        company.setLegalName(application.getLegalName());
        company.setTradingName(application.getTradingName());
        company.setRegistrationNumber(application.getRegistrationNumber());
        company.setCountry(application.getCountry());
        company.setBusinessType(application.getBusinessType());
        company.setStatus(GlobalStatuses.ACTIVE); // Approved and active
        company.setApprovedAt(LocalDateTime.now());
        company.setCompanyIdentifierCode(generatorUtils.generateCustomerId());
        Company savedCompany = companyRepository.save(company);
        log.info("Company created with ID: {}, Customer ID: {}",
                savedCompany.getId(), savedCompany.getCompanyIdentifierCode());

        // 2. Create Communication
        CompanyCommunicationDetails communication = getCompanyCommunicationDetails(application, savedCompany);

        communicationRepository.save(communication);
        log.info("Communication details saved for company: {}", savedCompany.getCompanyIdentifierCode());

        // 3. Create Credentials with API keys
        CompanyCredentials credentials = createCredentials(savedCompany);
        credentialsRepository.save(credentials);
        log.info("API credentials generated for company: {}", savedCompany.getCompanyIdentifierCode());

        return savedCompany;
    }

    private CompanyCommunicationDetails getCompanyCommunicationDetails(CompanyApplication application, Company savedCompany) {
        CompanyCommunicationDetails communication = new CompanyCommunicationDetails();
        communication.setCompany(savedCompany);
        communication.setEmail(application.getEmail());
        communication.setPhone(application.getPhone());
        communication.setWebsite(application.getWebsite());
        communication.setSecondaryEmail(application.getContactPersonEmail());
        communication.setSecondaryPhone(application.getContactPersonPhone());
        communication.setEmailNotifications(true); // Default enabled
        communication.setWebhookEnabled(false); // Will be configured later
        return communication;
    }

    private CompanyCredentials createCredentials(Company company) {
        // Generate API key and secret
        String apiKey = generatorUtils.generateApiKey();
        String apiSecret = generatorUtils.generateApiSecret();

        // Hash them for storage
        String apiKeyHash = generatorUtils.hashString(apiKey);
        String apiSecretHash = generatorUtils.hashString(apiSecret);

        CompanyCredentials credentials = getCompanyCredentials(company, apiKeyHash, apiSecretHash);

        // TODO: Store plaintext keys in a secure way to send via email
        // For now, log them (ONLY FOR DEVELOPMENT)
        log.warn("⚠️ API KEY (SEND VIA EMAIL): {}", apiKey);
        log.warn("⚠️ API SECRET (SEND VIA EMAIL): {}", apiSecret);
        log.info("⚠️ These keys are hashed in database and cannot be retrieved");

        return credentials;
    }

    private CompanyCredentials getCompanyCredentials(Company company, String apiKeyHash, String apiSecretHash) {
        CompanyCredentials credentials = new CompanyCredentials();
        credentials.setCompany(company);
        credentials.setApiKeyHash(apiKeyHash);
        credentials.setApiSecretHash(apiSecretHash);
        credentials.setStatus(GlobalStatuses.ACTIVE);
        credentials.setRateLimitPerHour(100); // Default limits
        credentials.setRateLimitPerDay(1000);
        credentials.setTotalRequests(0L);
        credentials.setTotalSuccessfulRequests(0L);
        credentials.setTotalFailedRequests(0L);
        return credentials;
    }

    @Override
    public Company getCompanyById(String companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found: " + companyId
                ));
    }

    @Override
    public Company getCompanyByCustomerId(String companyIdentifierCode) {
        return  companyRepository.findByCompanyIdentifierCode(companyIdentifierCode)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found: " + companyIdentifierCode
                ));
    }

    @Override
    public void suspendCompany(String id, String adminId, String reason) {
        Company company = getCompanyById(id);

        if (company.getStatus() == GlobalStatuses.SUSPENDED) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Company is already suspended"
            );
        }

        company.setStatus(GlobalStatuses.SUSPENDED);
        companyRepository.save(company);

        // Also suspend credentials
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(id)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"
                ));

        credentials.setStatus(GlobalStatuses.SUSPENDED);
        credentialsRepository.save(credentials);

        log.info("Company {} suspended by admin {}. Reason: {}",
                company.getId(), adminId, reason);

        // TODO: Send suspension email
        // emailService.sendSuspensionEmail(company, reason);
    }

    @Override
    public void activateCompany(String companyId, String adminId) {
        Company company = getCompanyById(companyId);

        if (company.getStatus() == GlobalStatuses.ACTIVE) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Company is already active"
            );
        }

        company.setStatus(GlobalStatuses.ACTIVE);
        companyRepository.save(company);

        // Also activate credentials
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"
                ));

        credentials.setStatus(GlobalStatuses.ACTIVE);
        credentialsRepository.save(credentials);

        log.info("Company {} activated by admin {}", company.getCompanyIdentifierCode(), adminId);

        // TODO: Send activation email
        // emailService.sendActivationEmail(company);
    }
}