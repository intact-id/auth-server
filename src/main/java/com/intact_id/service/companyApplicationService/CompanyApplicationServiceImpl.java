package com.intact_id.service.companyApplicationService;

import com.intact_id.constant.enums.Country;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.repository.CompanyApplicationRepository;
import com.intact_id.dto.request.CompanyApplicationRequestDto;
import com.intact_id.dto.response.CompanyApplicationCreationResponseDto;
import com.intact_id.dto.response.CompanyApplicationResponseDto;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.generator.GeneratorUtils;
import com.intact_id.util.mapper.MapperUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 19:03
 */

@Slf4j
@Service
@AllArgsConstructor
public class CompanyApplicationServiceImpl implements CompanyApplicationService {

    private final CompanyApplicationRepository applicationRepository;
    private final GeneratorUtils generatorUtils;
    private final MapperUtils mapperUtils;

    @Override
    public CompanyApplicationCreationResponseDto submitApplication(CompanyApplicationRequestDto request) {

        log.info("Processing application submission for company: {}", request.getLegalName());

        if (hasExistingApplication(request.getEmail(), request.getRegistrationNumber())) {
            log.warn("Duplicate application attempt for email: {}, registration number: {}",
                    request.getEmail(), request.getRegistrationNumber());
            throw new ApplicationExceptions.DuplicateResourceException(
                    "An application with this email or registration number already exists"
            );
        }

        String applicationNumber = generatorUtils.generateApplicationNumber();

        CompanyApplication application = getCompanyApplication(request, applicationNumber);

        application.setCountry(request.getCountry().getIso2());

        var persistedCompanyApplication = applicationRepository.save(application);

        log.info("Application submitted successfully with ID: {}", persistedCompanyApplication.getApplicationNumber());

        return CompanyApplicationCreationResponseDto.builder()
                .applicationNumber(persistedCompanyApplication.getApplicationNumber())
                .submittedAt(persistedCompanyApplication.getCreatedAt())
                .message("Application submitted successfully with ID: " + persistedCompanyApplication.getApplicationNumber())
                .status(persistedCompanyApplication.getApplicationStatus())
                .build();
    }


    @Override
    public CompanyApplicationResponseDto getApplication(String applicationId) {
        log.info("Fetching application with ID: {}", applicationId);

        var companyApplication = applicationRepository.findByApplicationNumber(applicationId)
                .orElseThrow(() -> {
                    log.error("Application with ID: {} not found", applicationId);
                    return new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Application with ID: " + applicationId + " not found"
                    );
                });

        return mapperUtils.toApplicationResponseDto(companyApplication);
    }

    @Override
    public Page<CompanyApplicationResponseDto> getAllApplications(GlobalStatuses status, Pageable pageable) {
        log.info("Fetching all applications with status: {}", status);

        Page<CompanyApplication> pageCompanyApplications = (status != null) ?
                applicationRepository.findByApplicationStatus(status, pageable) :
                applicationRepository.findAll(pageable);

        if (pageCompanyApplications.isEmpty()) {
            log.warn("No applications found for status: {}", status);
            throw new ApplicationExceptions.IntactCompanyNotFoundException("No applications found");
        }

        return pageCompanyApplications.map(mapperUtils::toApplicationResponseDto);
    }


    @Override
    public void deleteApplication(String applicationId) {
        log.info("Deleting application with ID: {}", applicationId);

        CompanyApplication application = applicationRepository.findByApplicationNumber(applicationId)
                .orElseThrow(() -> {
                    log.error("Application with ID: {} not found", applicationId);
                    return new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Application with ID: " + applicationId + " not found"
                    );
                });

        applicationRepository.delete(application);
        log.info("Application with ID: {} deleted successfully", applicationId);
    }

    @Override
    public boolean hasExistingApplication(String email, String registrationNumber) {
        log.info("Checking existing application for email: {} or registration number: {}", email, registrationNumber);
        return applicationRepository.existsByEmailIgnoreCaseOrRegistrationNumberIgnoreCase(email, registrationNumber);
    }

    private CompanyApplication getCompanyApplication(CompanyApplicationRequestDto request, String applicationNumber) {
        CompanyApplication application = new CompanyApplication();

        application.setApplicationNumber(applicationNumber);
        application.setLegalName(request.getLegalName());
        application.setTradingName(request.getTradingName());
        application.setRegistrationNumber(request.getRegistrationNumber());
        application.setEmail(request.getEmail());
        application.setPhone(request.getPhone());
        application.setWebsite(request.getWebsite());
        application.setCountry(String.valueOf(request.getCountry()));
        application.setBusinessType(request.getBusinessType());
        application.setContactPersonName(request.getContactPersonName());
        application.setContactPersonTitle(request.getContactPersonTitle());
        application.setContactPersonEmail(request.getContactPersonEmail());
        application.setContactPersonPhone(request.getContactPersonPhone());
        return application;
    }


}
