package com.intact_id.service.companyApplicationService;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.model.CompanyApplicationApproval;
import com.intact_id.db.repository.CompanyApplicationApprovalRepository;
import com.intact_id.db.repository.CompanyApplicationRepository;
import com.intact_id.dto.request.CompanyApplicationApprovalRequestDto;
import com.intact_id.dto.request.CompanyApplicationRequestDto;
import com.intact_id.dto.response.ApplicationApprovalResponseDto;
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

import java.time.LocalDateTime;

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
    private final CompanyApplicationApprovalRepository approvalRepository;
    private final GeneratorUtils generatorUtils;
    private final MapperUtils mapperUtils;

    // ================= SUBMIT APPLICATION =================

    @Override
    public CompanyApplicationCreationResponseDto submitApplication(CompanyApplicationRequestDto request) {

        log.info("Processing application submission for company: {}", request.getLegalName());

        if (hasExistingApplication(request.getEmail(), request.getRegistrationNumber())) {
            log.warn("Duplicate application for email: {}, registration: {}", request.getEmail(), request.getRegistrationNumber());
            throw new ApplicationExceptions.DuplicateResourceException(
                    "An application with this email or registration number already exists"
            );
        }

        String applicationNumber = generatorUtils.generateApplicationNumber();

        CompanyApplication application = getCompanyApplication(request, applicationNumber);

        application.setCountry(request.getCountry().getIso2());

        var saved = applicationRepository.save(application);

        log.info("Application submitted successfully: {}", saved.getApplicationNumber());

        return CompanyApplicationCreationResponseDto.builder()
                .applicationNumber(saved.getApplicationNumber())
                .submittedAt(saved.getCreatedAt())
                .message("Application submitted successfully with ID: " + saved.getApplicationNumber())
                .status(saved.getApplicationStatus())
                .build();
    }

    // ================= GET ONE APPLICATION =================

    @Override
    public CompanyApplicationResponseDto getApplication(String applicationId) {
        log.info("Fetching application: {}", applicationId);

        CompanyApplication application = applicationRepository.findByApplicationNumber(applicationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Application with ID: " + applicationId + " not found"
                ));

        return mapperUtils.toApplicationResponseDto(application);
    }

    // ================= GET ALL APPLICATIONS =================

    @Override
    public Page<CompanyApplicationResponseDto> getAllApplications(GlobalStatuses status, Pageable pageable) {
        log.info("Fetching all applications with status: {}", status);

        Page<CompanyApplication> applications = (status != null)
                ? applicationRepository.findByApplicationStatus(status, pageable)
                : applicationRepository.findAll(pageable);

        if (applications.isEmpty()) {
            throw new ApplicationExceptions.IntactCompanyNotFoundException("No applications found");
        }

        return applications.map(mapperUtils::toApplicationResponseDto);
    }

    // ================= DELETE APPLICATION =================

    @Override
    public void deleteApplication(String applicationId) {
        log.info("Deleting application: {}", applicationId);

        CompanyApplication application = applicationRepository.findByApplicationNumber(applicationId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Application with ID: " + applicationId + " not found"
                ));

        applicationRepository.delete(application);
        log.info("Application {} deleted", applicationId);
    }

    // ================= CHECK DUPLICATE =================

    @Override
    public boolean hasExistingApplication(String email, String registrationNumber) {
        return applicationRepository.existsByEmailIgnoreCaseOrRegistrationNumberIgnoreCase(email, registrationNumber);
    }

    @Override
    public ApplicationApprovalResponseDto performMakerAction(CompanyApplicationApprovalRequestDto request) {

        log.info("Maker action initiated for application: {}", request.getApplicationId());
        if (request.getPerformedBy() == null || request.getPerformedBy().isBlank()) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Authenticated maker is required");
        }

        CompanyApplication application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Application not found: " + request.getApplicationId()
                ));

        if (!isValidForMakerAction(application.getApplicationStatus())) {
            throw new ApplicationExceptions.InvalidActionException(
                    "Application is not in valid state. Current status: " + application.getApplicationStatus()
            );
        }

        boolean alreadyActioned = approvalRepository.existsByApplicationIdAndPerformedBy(
                request.getApplicationId(), request.getPerformedBy());

        if (alreadyActioned) {
            throw new ApplicationExceptions.DuplicateResourceException(
                    "You have already performed an action on this application"
            );
        }

        var approval = createApproval(request);

        updateApplicationStatus(application, request.getPerformedByAction());

        var savedApproval = approvalRepository.save(approval);
        applicationRepository.save(application);

        return mapperUtils.toApplicationApprovalResponseDto(savedApproval);
    }


    @Override
    public ApplicationApprovalResponseDto getApproval(String approvalId) {

        CompanyApplicationApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "Approval not found: " + approvalId
                ));

        return mapperUtils.toApplicationApprovalResponseDto(approval);
    }

    @Override
    public long countApprovalsByApplication(String applicationId) {
        return approvalRepository.countByApplicationId(applicationId);
    }

    @Override
    public boolean hasAdminActioned(String applicationId, String adminId) {
        return approvalRepository.existsByApplicationIdAndPerformedBy(applicationId, adminId);
    }


    private CompanyApplicationApproval createApproval(CompanyApplicationApprovalRequestDto request) {
        CompanyApplicationApproval approval = new CompanyApplicationApproval();

        approval.setApplicationId(request.getApplicationId());
        approval.setPerformedBy(request.getPerformedBy());
        approval.setPerformedByAction(request.getPerformedByAction());
        approval.setApproverNote(request.getApproverNote());
        approval.setEndUserMessage(request.getEndUserMessage());
        approval.setActionPerformedAt(LocalDateTime.now());

        return approval;
    }

    private boolean isValidForMakerAction(GlobalStatuses currentStatus) {
        return currentStatus == GlobalStatuses.SUBMITTED ||
                currentStatus==GlobalStatuses.PENDING ||
                currentStatus == GlobalStatuses.INFO_REQUESTED;
    }

    private void updateApplicationStatus(CompanyApplication application, GlobalStatuses makerAction) {

        switch (makerAction) {
            case APPROVED:
                application.setApplicationStatus(GlobalStatuses.PENDING_APPROVAL);
                break;

            case REJECTED:
                application.setApplicationStatus(GlobalStatuses.REJECTED);
                break;

            case INFO_REQUESTED:
                application.setApplicationStatus(GlobalStatuses.INFO_REQUESTED);
                break;

            default:
                throw new ApplicationExceptions.InvalidActionException("Invalid maker action: " + makerAction);
        }
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
        application.setCountry(request.getCountry().getIso2());
        application.setBusinessType(request.getBusinessType());
        application.setContactPersonName(request.getContactPersonName());
        application.setContactPersonTitle(request.getContactPersonTitle());
        application.setContactPersonEmail(request.getContactPersonEmail());
        application.setContactPersonPhone(request.getContactPersonPhone());

        return application;
    }
}
