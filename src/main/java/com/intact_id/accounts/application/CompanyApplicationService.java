package com.intact_id.accounts.application;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.web.CompanyApplicationApprovalRequestDto;
import com.intact_id.accounts.web.CompanyApplicationRequestDto;
import com.intact_id.accounts.web.ApplicationApprovalResponseDto;
import com.intact_id.accounts.web.CompanyApplicationCreationResponseDto;
import com.intact_id.accounts.web.CompanyApplicationResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 21/11/2025 19:12
 */

public interface CompanyApplicationService {
    CompanyApplicationCreationResponseDto submitApplication(CompanyApplicationRequestDto request);

    CompanyApplicationResponseDto getApplication(String applicationId);

    Page<CompanyApplicationResponseDto> getAllApplications(GlobalStatuses status, Pageable pageable);

    void deleteApplication(String applicationId);

    boolean hasExistingApplication(String email, String registrationNumber);
    /**
     * Perform maker action (approve/reject/request-info)
     */
    ApplicationApprovalResponseDto performMakerAction(CompanyApplicationApprovalRequestDto request);

    /**
     * Get approval by ID
     */
    ApplicationApprovalResponseDto getApproval(String approvalId);

    /**
     * Count approvals for an application
     */
    long countApprovalsByApplication(String applicationId);

    /**
     * Check if admin already actioned this application
     */
    boolean hasAdminActioned(String applicationId, String adminId);

}
