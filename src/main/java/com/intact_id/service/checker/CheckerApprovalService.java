package com.intact_id.service.checker;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.db.model.CompanyApplication;
import com.intact_id.db.model.CompanyApplicationApproval;
import com.intact_id.db.repository.CompanyApplicationApprovalRepository;
import com.intact_id.db.repository.CompanyApplicationRepository;
import com.intact_id.db.repository.CompanyRepository;
import com.intact_id.db.repository.user.UserRepository;
import com.intact_id.dto.request.ApplicationApprovalDecisionDto;
import com.intact_id.dto.response.ApplicationApprovalResponseDto;
import com.intact_id.dto.response.CheckerApprovalQueueResponseDto;
import com.intact_id.service.CompanyService.CompanyService;
import com.intact_id.service.email.EmailService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.util.mapper.MapperUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 14:07
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckerApprovalService {

    private final CompanyApplicationApprovalRepository approvalRepository;
    private final CompanyApplicationRepository applicationRepository;
    private final CompanyService companyService;
    private final MapperUtils mapperUtils;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public Page<CheckerApprovalQueueResponseDto> getPendingCheckerApprovals(Pageable pageable) {
        var pendingApprovals = approvalRepository.findByFinalActionIsNullAndPerformedByAction(
                GlobalStatuses.APPROVED,
                pageable
        );

        var applicationIds = pendingApprovals.stream()
                .map(CompanyApplicationApproval::getApplicationId)
                .collect(Collectors.toSet());

        var applicationsById = applicationRepository.findAllById(applicationIds).stream()
                .collect(Collectors.toMap(CompanyApplication::getId, Function.identity()));

        var makerIds = pendingApprovals.stream()
                .map(CompanyApplicationApproval::getPerformedBy)
                .collect(Collectors.toSet());

        var makersById = userRepository.findAllById(makerIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), Function.identity()));

        return pendingApprovals.map(approval -> {
            var application = applicationsById.get(approval.getApplicationId());
            var maker = makersById.get(approval.getPerformedBy());

            return CheckerApprovalQueueResponseDto.builder()
                    .approvalId(approval.getId())
                    .applicationId(approval.getApplicationId())
                    .applicationNumber(application != null ? application.getApplicationNumber() : null)
                    .legalName(application != null ? application.getLegalName() : null)
                    .registrationNumber(application != null ? application.getRegistrationNumber() : null)
                    .email(application != null ? application.getEmail() : null)
                    .businessType(application != null ? application.getBusinessType() : null)
                    .applicationStatus(application != null ? application.getApplicationStatus() : null)
                    .makerId(approval.getPerformedBy())
                    .makerUsername(maker != null ? maker.getUsername() : null)
                    .makerEmail(maker != null ? maker.getEmail() : null)
                    .makerAction(approval.getPerformedByAction())
                    .makerNote(approval.getApproverNote())
                    .submittedForApprovalAt(approval.getActionPerformedAt())
                    .build();
        });
    }

    @Transactional
    public ApplicationApprovalResponseDto performCheckerApproval(ApplicationApprovalDecisionDto request) {
        log.info("Checker approval initiated for approval: {}", request.getApprovalId());
        if (Objects.isNull(request.getApprovedBy()) || request.getApprovedBy().isBlank()) {
            throw new ApplicationExceptions.IntactUnauthorizedException("Authenticated checker is required");
        }

        // 1. Get the maker's approval record
        var makerApproval = approvalRepository.findById(request.getApprovalId())
                .orElseThrow(() -> {
                    log.error("Approval not found: {}", request.getApprovalId());
                    return new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Approval not found: " + request.getApprovalId());
                });

        // 2. Get the application
        var application = applicationRepository.findById(makerApproval.getApplicationId())
                .orElseThrow(() -> {
                    log.error("Application not found: {}", makerApproval.getApplicationId());
                    return new ApplicationExceptions.IntactCompanyNotFoundException(
                            "Application not found");
                });

        // 3. Validate application is in correct state
        if (application.getApplicationStatus() != GlobalStatuses.PENDING_APPROVAL) {
            log.error("Application {} not in PENDING_APPROVAL state. Current: {}",
                    application.getId(), application.getApplicationStatus());
            throw new ApplicationExceptions.InvalidActionException(
                    "Application is not pending approval. Current status: "
                            + application.getApplicationStatus());
        }

        // 4. CRITICAL: Ensure checker != maker (different person)
        if (makerApproval.getPerformedBy().equals(request.getApprovedBy())) {
            log.error("Checker {} cannot be same as Maker {}",
                    request.getApprovedBy(), makerApproval.getPerformedBy());
            throw new ApplicationExceptions.InvalidActionException(
                    "Checker cannot be the same person as Maker");
        }

        // 5. Handle checker decision
        if (request.getFinalAction() == GlobalStatuses.APPROVED) {
            return handleApproval(makerApproval, application, request);
        } else if (request.getFinalAction() == GlobalStatuses.REJECTED) {
            return handleRejection(makerApproval, application, request);
        } else {
            throw new ApplicationExceptions.InvalidActionException(
                    "Invalid checker action: " + request.getFinalAction());
        }
    }

    private ApplicationApprovalResponseDto handleApproval(
            CompanyApplicationApproval makerApproval,
            CompanyApplication application,
            ApplicationApprovalDecisionDto request) {
        log.info("Checker approving application: {}", application.getId());

        // 1. Create the company (Company + Communication + Credentials)
        // This will also trigger the approval email with API keys
        Company company = companyService.createCompanyFromApplication(application);

        makerApproval.setApprovedBy(request.getApprovedBy());
        makerApproval.setFinalAction(GlobalStatuses.APPROVED);
        makerApproval.setApproverNote(
                makerApproval.getApproverNote() + "\n[CHECKER]: " + request.getApproverNote());
        makerApproval.setActionApprovedAt(LocalDateTime.now());

        // 3. Update application status and link to company
        application.setApplicationStatus(GlobalStatuses.APPROVED);

        makerApproval.setApplicationId(application.getId());
        // 4. Save everything
        var savedApproval = approvalRepository.save(makerApproval);
        applicationRepository.save(application);

        log.info("Application {} approved. Company {} created with API keys",
                application.getId(), company.getId());

        company.setApprovedBy(savedApproval.getApprovedBy());
        company.setApplicationId(savedApproval.getApplicationId());
        companyRepository.save(company);

        return mapperUtils.toApplicationApprovalResponseDto(savedApproval);
    }

    private ApplicationApprovalResponseDto handleRejection(
            CompanyApplicationApproval makerApproval,
            CompanyApplication application,
            ApplicationApprovalDecisionDto request) {
        log.info("Checker rejecting application: {}", application.getId());

        // 1. Update maker approval with rejection
        makerApproval.setApprovedBy(request.getApprovedBy());
        makerApproval.setFinalAction(GlobalStatuses.REJECTED);
        makerApproval.setApproverNote(
                makerApproval.getApproverNote() + "\n[CHECKER REJECTED]: " + request.getApproverNote());
        makerApproval.setActionApprovedAt(LocalDateTime.now());

        // 2. Update application status
        application.setApplicationStatus(GlobalStatuses.REJECTED);

        // 3. Save
        CompanyApplicationApproval savedApproval = approvalRepository.save(makerApproval);
        applicationRepository.save(application);

        log.info("Application {} rejected by checker", application.getId());

        // 4. Send rejection email
        try {
            emailService.sendCompanyRejectionEmail(
                    application.getEmail(),
                    application.getLegalName(),
                    request.getApproverNote());
            log.info("Rejection email sent to {}", application.getEmail());
        } catch (Exception e) {
            log.error("Failed to send rejection email to {}", application.getEmail(), e);
        }

        return mapperUtils.toApplicationApprovalResponseDto(savedApproval);
    }
}
