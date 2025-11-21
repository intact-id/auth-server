package com.intact_id.service.companyApplicationService;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.dto.request.CompanyApplicationRequestDto;
import com.intact_id.dto.response.CompanyApplicationCreationResponseDto;
import com.intact_id.dto.response.CompanyApplicationResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

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

}
