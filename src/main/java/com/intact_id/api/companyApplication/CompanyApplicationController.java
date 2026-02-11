package com.intact_id.api.companyApplication;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.dto.request.CompanyApplicationRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.CompanyApplicationCreationResponseDto;
import com.intact_id.dto.response.CompanyApplicationResponseDto;
import com.intact_id.service.companyApplicationService.CompanyApplicationService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "Company Applications", description = "Public and Admin endpoints for company applications")
public class CompanyApplicationController {
    private final CompanyApplicationService companyApplicationService;


    @PostMapping("/api/public/companies/register")
    @Operation(summary = "Submit company registration application (Public)")
    public ResponseEntity<ApiResponse<CompanyApplicationCreationResponseDto>> registerCompany(
            @Valid @RequestBody CompanyApplicationRequestDto request) {

        try {
            log.info("Public registration request for: {}", request.getLegalName());
            var response = companyApplicationService.submitApplication(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<CompanyApplicationCreationResponseDto>builder()
                            .success(true)
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("Application submitted successfully")
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.DuplicateResourceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.<CompanyApplicationCreationResponseDto>builder()
                            .responseCode(HttpStatus.CONFLICT.value())
                            .responseMessage("An existing application already exists")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }

    @GetMapping("/api/public/applications/{appId}/status")
    @Operation(summary = "Get application status (Public)")
    public ResponseEntity<ApiResponse<CompanyApplicationResponseDto>> getPublicApplicationStatus(
            @PathVariable String appId) {

        try {
            var response = companyApplicationService.getApplication(appId);
            return ResponseEntity.ok(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Application status retrieved")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application not found")
                            .errorMessage(e.getMessage())
                            .success(false)
                            .isInternalError(false)
                            .build());
        }
    }

    // ============ ADMIN ENDPOINTS ============

    @GetMapping("/api/admin/applications")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get all applications (Admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Page<CompanyApplicationResponseDto>>> getAllApplications(
            @RequestParam(required = false) GlobalStatuses status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("Admin fetching applications | Status: {}, Page: {}, Size: {}", status, page, size);
            Pageable pageable = PageRequest.of(page, size);
            var applications = companyApplicationService.getAllApplications(status, pageable);

            return ResponseEntity.ok(
                    ApiResponse.<Page<CompanyApplicationResponseDto>>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Applications fetched successfully")
                            .success(true)
                            .data(applications)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Page<CompanyApplicationResponseDto>>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Applications not found")
                            .success(false)
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }

    @GetMapping("/api/admin/applications/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get application by ID (Admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<CompanyApplicationResponseDto>> getApplicationById(
            @Parameter(description = "Application ID") @PathVariable String id) {

        try {
            var response = companyApplicationService.getApplication(id);
            return ResponseEntity.ok(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Application fetched successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application not found")
                            .errorMessage(e.getMessage())
                            .success(false)
                            .isInternalError(false)
                            .build());
        }
    }

    @DeleteMapping("/api/admin/applications/{applicationId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete application (Admin)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> deleteApplication(
            @PathVariable String applicationId) {

        try {
            log.info("Admin deleting application {}", applicationId);
            companyApplicationService.deleteApplication(applicationId);

            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(
                    ApiResponse.<Void>builder()
                            .responseMessage("Application deleted successfully")
                            .responseCode(HttpStatus.NO_CONTENT.value())
                            .success(true)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Void>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application not found")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build());
        }
    }
}