package com.intact_id.service.CompanyService;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.model.CompanyCommunicationDetails;
import com.intact_id.db.model.CompanyCredentials;
import com.intact_id.db.repository.CompanyCommunicationRepository;
import com.intact_id.db.repository.CompanyCredentialsRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.dto.request.CompanyUpdateRequestDto;
import com.intact_id.dto.request.WebhookSettingsRequestDto;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.TenantContext;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
    private final com.intact_id.service.email.EmailService emailService;
    private final com.intact_id.service.NotificationService.NotificationService notificationService;

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
        CompanyCredentials credentials = createCredentials(savedCompany, communication.getEmail());
        credentialsRepository.save(credentials);
        log.info("API credentials generated for company: {}", savedCompany.getCompanyIdentifierCode());

        return savedCompany;
    }

    private CompanyCommunicationDetails getCompanyCommunicationDetails(CompanyApplication application,
                                                                       Company savedCompany) {
        CompanyCommunicationDetails communication = new CompanyCommunicationDetails();
        communication.setCompany(savedCompany);
        communication.setEmail(application.getEmail());
        communication.setPhone(application.getPhone());
        communication.setWebsite(application.getWebsite());
        communication.setSecondaryEmail(application.getContactPersonEmail());
        communication.setSecondaryPhone(application.getContactPersonPhone());
        communication.setEmailNotifications(true); // Default enabled
        communication.setWebhookEnabled(false); // Will be configured later
        communication.setWebhookSecret(generatorUtils.generateWebhookSecret());
        return communication;
    }

    private CompanyCredentials createCredentials(Company company, String email) {
        // On approval issue DEV credentials only — PROD keys are generated on demand
        String apiKey = generatorUtils.generateDevApiKey();
        String apiSecret = generatorUtils.generateDevApiSecret();

        // Hash them for storage
        String apiKeyHash = generatorUtils.hashString(apiKey);
        String apiSecretHash = generatorUtils.hashString(apiSecret);

        CompanyCredentials credentials = getCompanyCredentials(company, apiKeyHash, apiSecretHash);

        // Send email with plaintext keys
        try {
            emailService.sendCompanyApprovalEmail(email, company.getLegalName(), apiKey, apiSecret);
            log.info("Approval email sent to {} with API keys", email);

            // Persist notification to database
            try {
                notificationService.persistNotification(
                        company.getId(),
                        "Application Approved",
                        "Your company application has been approved. API credentials have been sent to your email.",
                        "SUCCESS");
            } catch (Exception ne) {
                log.error("Failed to persist approval notification", ne);
            }
        } catch (Exception e) {
            log.error("Failed to send approval email to {}", email, e);
        }

        return credentials;
    }

    private CompanyCredentials getCompanyCredentials(Company company, String devApiKeyHash, String devApiSecretHash) {
        CompanyCredentials credentials = new CompanyCredentials();
        credentials.setCompany(company);
        // DEV keys — issued on approval
        credentials.setDevApiKeyHash(devApiKeyHash);
        credentials.setDevApiSecretHash(devApiSecretHash);
        credentials.setDevStatus(GlobalStatuses.ACTIVE);
        credentials.setDevRateLimitPerHour(100);
        credentials.setDevRateLimitPerDay(1000);
        credentials.setDevTotalRequests(0L);
        credentials.setDevTotalSuccessfulRequests(0L);
        credentials.setDevTotalFailedRequests(0L);
        // PROD keys are null — generated on demand
        credentials.setProdStatus(GlobalStatuses.ACTIVE); // ready to activate once keys generated
        return credentials;
    }

    @Override
    public Company getCompanyById(String companyId) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        return companyRepository.findById(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found: " + resolvedCompanyId));
    }

    @Override
    public Company getCompanyByCustomerId(String companyIdentifierCode) {
        if (!TenantContext.isSuperAdmin()) {
            Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
            if (company.getCompanyIdentifierCode() == null ||
                    !company.getCompanyIdentifierCode().equals(companyIdentifierCode)) {
                throw new ApplicationExceptions.IntactUnauthorizedException("Access denied");
            }
        }

        return companyRepository.findByCompanyIdentifierCode(companyIdentifierCode)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Company not found: " + companyIdentifierCode));
    }

    @Override
    public Company suspendCompany(String id, String adminId, String reason) {
        Company company = getCompanyById(id);
        String resolvedCompanyId = company.getId();

        if (company.getStatus() == GlobalStatuses.SUSPENDED) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Company is already suspended");
        }

        company.setStatus(GlobalStatuses.SUSPENDED);
        Company savedCompany = companyRepository.save(company);

        // Also suspend credentials
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"));

        credentials.setStatus(GlobalStatuses.SUSPENDED);
        credentialsRepository.save(credentials);

        log.info("Company {} suspended by admin {}. Reason: {}",
                company.getId(), adminId, reason);

        // Send suspension email
        try {
            CompanyCommunicationDetails comms = getCompanyCommunication(resolvedCompanyId);
            emailService.sendCompanySuspensionEmail(comms.getEmail(), company.getLegalName(), reason);

            // Persist notification to database
            try {
                notificationService.persistNotification(
                        company.getId(),
                        "Account Suspended",
                        "Your account has been suspended. Reason: " + reason,
                        "WARNING");
            } catch (Exception ne) {
                log.error("Failed to persist suspension notification", ne);
            }
        } catch (Exception e) {
            log.error("Failed to send suspension email", e);
        }

        return savedCompany;
    }

    @Override
    public Company activateCompany(String companyId, String adminId) {
        Company company = getCompanyById(companyId);
        String resolvedCompanyId = company.getId();

        if (company.getStatus() == GlobalStatuses.ACTIVE) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Company is already active");
        }

        company.setStatus(GlobalStatuses.ACTIVE);
        Company savedCompany = companyRepository.save(company);

        // Also activate credentials
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"));

        credentials.setStatus(GlobalStatuses.ACTIVE);
        credentialsRepository.save(credentials);

        log.info("Company {} activated by admin {}", company.getCompanyIdentifierCode(), adminId);

        // Send activation email
        try {
            CompanyCommunicationDetails comms = getCompanyCommunication(resolvedCompanyId);
            emailService.sendCompanyActivationEmail(comms.getEmail(), company.getLegalName());

            // Persist notification to database
            try {
                notificationService.persistNotification(
                        company.getId(),
                        "Account Activated",
                        "Your account has been activated and is now ready to use.",
                        "SUCCESS");
            } catch (Exception ne) {
                log.error("Failed to persist activation notification", ne);
            }
        } catch (Exception e) {
            log.error("Failed to send activation email", e);
        }

        return savedCompany;
    }

    @Override
    @Transactional
    public void suspendCredentials(String companyId, String environment) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"));
        if ("dev".equalsIgnoreCase(environment)) {
            credentials.setDevStatus(GlobalStatuses.SUSPENDED);
        } else if ("prod".equalsIgnoreCase(environment)) {
            credentials.setProdStatus(GlobalStatuses.SUSPENDED);
            credentials.setStatus(GlobalStatuses.SUSPENDED);
        } else {
            // suspend all
            credentials.setDevStatus(GlobalStatuses.SUSPENDED);
            credentials.setProdStatus(GlobalStatuses.SUSPENDED);
            credentials.setStatus(GlobalStatuses.SUSPENDED);
        }
        credentialsRepository.save(credentials);
        log.info("Credentials ({}) suspended for company: {}", environment, resolvedCompanyId);
    }

    @Override
    @Transactional
    public void activateCredentials(String companyId, String environment) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        CompanyCredentials credentials = credentialsRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Credentials not found for company"));
        if ("dev".equalsIgnoreCase(environment)) {
            credentials.setDevStatus(GlobalStatuses.ACTIVE);
        } else if ("prod".equalsIgnoreCase(environment)) {
            credentials.setProdStatus(GlobalStatuses.ACTIVE);
            credentials.setStatus(GlobalStatuses.ACTIVE);
        } else {
            // activate all
            credentials.setDevStatus(GlobalStatuses.ACTIVE);
            credentials.setProdStatus(GlobalStatuses.ACTIVE);
            credentials.setStatus(GlobalStatuses.ACTIVE);
        }
        credentialsRepository.save(credentials);
        log.info("Credentials ({}) activated for company: {}", environment, resolvedCompanyId);
    }

    @Override
    @Transactional
    public Company updateCompany(String companyId, CompanyUpdateRequestDto request) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        log.info("Updating company: {}", resolvedCompanyId);

        Company company = getCompanyById(resolvedCompanyId);

        // Update company fields
        if (request.getTradingName() != null) {
            company.setTradingName(request.getTradingName());
        }
        if (request.getBusinessType() != null) {
            company.setBusinessType(request.getBusinessType());
        }

        // Update communication details if any communication field is provided
        if (hasAnyCommunicationUpdate(request)) {
            CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(resolvedCompanyId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Communication details not found for company"));

            if (request.getEmail() != null) {
                communication.setEmail(request.getEmail());
            }
            if (request.getPhone() != null) {
                communication.setPhone(request.getPhone());
            }
            if (request.getWebsite() != null) {
                communication.setWebsite(request.getWebsite());
            }
            if (request.getSecondaryEmail() != null) {
                communication.setSecondaryEmail(request.getSecondaryEmail());
            }
            if (request.getSecondaryPhone() != null) {
                communication.setSecondaryPhone(request.getSecondaryPhone());
            }
            if (request.getSupportEmail() != null) {
                communication.setSupportEmail(request.getSupportEmail());
            }
            if (request.getBillingEmail() != null) {
                communication.setBillingEmail(request.getBillingEmail());
            }

            communicationRepository.save(communication);
            log.info("Communication details updated for company: {}", resolvedCompanyId);
        }

        return companyRepository.save(company);
    }

    private boolean hasAnyCommunicationUpdate(CompanyUpdateRequestDto request) {
        return request.getEmail() != null ||
                request.getPhone() != null ||
                request.getWebsite() != null ||
                request.getSecondaryEmail() != null ||
                request.getSecondaryPhone() != null ||
                request.getSupportEmail() != null ||
                request.getBillingEmail() != null;
    }

    @Override
    public Page<Company> getAllCompanies(GlobalStatuses status, String country, String businessType,
                                         Pageable pageable) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(null);
        if (resolvedCompanyId != null) {
            Company company = companyRepository.findById(resolvedCompanyId)
                    .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Company not found: " + resolvedCompanyId));
            return new PageImpl<>(java.util.List.of(company), pageable, 1);
        }

        log.info("Fetching companies with filters - Status: {}, Country: {}, BusinessType: {}", status, country,
                businessType);

        return companyRepository.findAll((root, query, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (country != null && !country.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("country"), country));
            }

            if (businessType != null && !businessType.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("businessType")),
                        "%" + businessType.toLowerCase() + "%"));
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pageable);
    }

    // ========== Webhook/Communication Methods ==========

    @Override
    public CompanyCommunicationDetails getCompanyCommunication(String companyId) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        log.info("Fetching communication details for company: {}", resolvedCompanyId);

        // Verify company exists
        getCompanyById(resolvedCompanyId);

        return communicationRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Communication details not found for company: " + resolvedCompanyId));
    }

    @Override
    @Transactional
    public CompanyCommunicationDetails updateWebhookSettings(String companyId, WebhookSettingsRequestDto request) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        log.info("Updating webhook settings for company: {}", resolvedCompanyId);

        // Verify company exists
        getCompanyById(resolvedCompanyId);

        CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Communication details not found for company: " + resolvedCompanyId));

        // Update webhook settings
        if (request.getWebhookUrl() != null) {
            communication.setWebhookUrl(request.getWebhookUrl().trim());
        }
        if (request.getWebhookEnabled() != null) {
            communication.setWebhookEnabled(request.getWebhookEnabled());
        }
        if (request.getEmailNotifications() != null) {
            communication.setEmailNotifications(request.getEmailNotifications());
        }
        if (request.getSmsNotifications() != null) {
            communication.setSmsNotifications(request.getSmsNotifications());
        }
        if (request.getPayloadFields() != null) {
            communication.setPayloadFields(request.getPayloadFields());
        }

        CompanyCommunicationDetails saved = communicationRepository.save(communication);
        log.info("Webhook settings updated successfully for company: {}", resolvedCompanyId);

        return saved;
    }

    @Override
    @Transactional
    public CompanyCommunicationDetails updateApplicantNotifications(String companyId, boolean onApproval, boolean onRejection, boolean onPendingReview) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Communication details not found for company: " + resolvedCompanyId));

        java.util.Map<String, Object> settings = communication.getNotificationSettings();
        if (settings == null) settings = new java.util.HashMap<>();
        settings.put("notifyApplicantOnApproval", onApproval);
        settings.put("notifyApplicantOnRejection", onRejection);
        settings.put("notifyApplicantOnPendingReview", onPendingReview);
        communication.setNotificationSettings(settings);

        return communicationRepository.save(communication);
    }

    @Override
    @Transactional
    public CompanyCommunicationDetails rotateWebhookSecret(String companyId) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        log.info("Rotating webhook secret for company: {}", resolvedCompanyId);

        CompanyCommunicationDetails communication = communicationRepository.findByCompanyId(resolvedCompanyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Communication details not found for company: " + resolvedCompanyId));

        communication.setWebhookSecret(generatorUtils.generateWebhookSecret());
        CompanyCommunicationDetails saved = communicationRepository.save(communication);
        log.info("Webhook secret rotated for company: {}", resolvedCompanyId);
        return saved;
    }

    @Override
    @Transactional
    public void deleteCompany(String companyId) {
        String resolvedCompanyId = resolveCompanyIdForAction(companyId);
        log.info("Deleting company: {}", resolvedCompanyId);

        Company company = getCompanyById(resolvedCompanyId);
        CompanyCommunicationDetails comms = getCompanyCommunication(resolvedCompanyId);

        // Delete communication details
        communicationRepository.findByCompanyId(resolvedCompanyId)
                .ifPresent(communication -> {
                    communicationRepository.delete(communication);
                    log.info("Communication details deleted for company: {}", resolvedCompanyId);
                });

        // Delete credentials
        credentialsRepository.findByCompanyId(resolvedCompanyId)
                .ifPresent(credentials -> {
                    credentialsRepository.delete(credentials);
                    log.info("Credentials deleted for company: {}", resolvedCompanyId);
                });

        // Delete company
        companyRepository.delete(company);

        log.info("Company {} deleted successfully", resolvedCompanyId);

//                 Send deletion notification email (Simple email for now)
        emailService.sendSimpleEmail(comms.getEmail(), "Account Deleted", "Your account has been deleted.");
    }

    private String resolveCompanyIdForAction(String companyId) {
        String resolvedCompanyId = TenantContext.resolveCompanyIdIfAuthenticated(companyId);
        if (resolvedCompanyId == null || resolvedCompanyId.isBlank()) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Company access denied");
        }
        return resolvedCompanyId;
    }
}
