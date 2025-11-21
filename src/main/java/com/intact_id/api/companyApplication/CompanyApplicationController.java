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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/company-applications")
@Validated
@Tag(
        name = "Company Applications",
        description = "Endpoints for submitting and managing company KYC applications"
)
public class CompanyApplicationController {
    private final CompanyApplicationService companyApplicationService;

    @PostMapping
    @Operation(summary = "Submit a new company application")
    public ResponseEntity<ApiResponse<CompanyApplicationCreationResponseDto>> submitApplication(
            @Valid @RequestBody CompanyApplicationRequestDto request) {

        try {
            log.info("Received company application for: {}", request.getLegalName());

            var companyApplicationResponse = companyApplicationService.submitApplication(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<CompanyApplicationCreationResponseDto>builder()
                            .success(true)
                            .responseCode(201)
                            .responseMessage("Company application submitted successfully")
                            .data(companyApplicationResponse)
                            .build()
            );
        } catch (ApplicationExceptions.DuplicateResourceException e) {
            log.error("duplicate application", e);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.<CompanyApplicationCreationResponseDto>builder()
                            .responseCode(HttpStatus.CONFLICT.value())
                            .responseMessage("An existing application already exists")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        }
    }

    @GetMapping("/{applicationNumber}")
    @Operation(summary = "Get application status by application ID")

    public ResponseEntity<ApiResponse<CompanyApplicationResponseDto>> getApplicationStatus(
            @Parameter(description = "Application ID")
            @PathVariable String applicationNumber) {

        try {
            log.info("Fetching application status for: {}", applicationNumber);

            var applicationResponse = companyApplicationService.getApplication(applicationNumber);

            return ResponseEntity.status(HttpStatus.OK).body(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Application fetched successfully")
                            .success(true)
                            .data(applicationResponse)
                            .build()
            );
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.error("Error fetching application {}", applicationNumber, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyApplicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application Not Found")
                            .errorMessage(e.getMessage())
                            .success(false)
                            .isInternalError(false)
                            .build()
            );
        }
    }

    @GetMapping
    @Operation(summary = "Get all applications (Admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Page<CompanyApplicationResponseDto>>> getAllApplications(
            @RequestParam(required = false) GlobalStatuses status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            log.info("Fetching applications | Status: {}, Page: {}, Size: {}", status, page, size);
            Pageable pageable = PageRequest.of(page, size);

            var pagedCompanyApplications = companyApplicationService.getAllApplications(status, pageable);

            return ResponseEntity.status(HttpStatus.OK).body(
                    ApiResponse.<Page<CompanyApplicationResponseDto>>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Applications fetched successfully")
                            .success(true)
                            .data(pagedCompanyApplications)
                            .build()
            );

        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.error("Error fetching applications", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Page<CompanyApplicationResponseDto>>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application Not Found")
                            .success(false)
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        }
    }

//    @PutMapping("/{applicationId}/status")
//    @Operation(summary = "Update application status (Admin only)")
//    @SecurityRequirement(name = "bearerAuth")
//    public ResponseEntity<ApiResponse<Map<String, Object>>> updateApplicationStatus(
//            @PathVariable String applicationId,
//            @RequestBody Map<String, Object> statusUpdate) {
//
//        try {
//            log.info("Updating {} to status {}", applicationId, statusUpdate.get("status"));
//
//            // MOCK
//            Map<String, Object> responseData = new HashMap<>();
//            responseData.put("applicationId", applicationId);
//            responseData.put("newStatus", statusUpdate.get("status"));
//            responseData.put("updatedAt", LocalDateTime.now());
//
//            return ok("Status updated successfully", responseData);
//        } catch (Exception e) {
//            log.error("Error updating application {}", applicationId, e);
//            return internalError("Failed to update status: " + e.getMessage());
//        }
//    }

    @DeleteMapping("/{applicationId}")
    @Operation(summary = "Delete or cancel a company application")

    public ResponseEntity<ApiResponse<Void>> deleteApplication(
            @PathVariable String applicationId) {

        try {
            log.info("Deleting application {}", applicationId);

            companyApplicationService.deleteApplication(applicationId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(
                    ApiResponse.<Void>builder()
                            .responseMessage("Application deleted successfully")
                            .responseCode(HttpStatus.NO_CONTENT.value())
                            .data(null)
                            .success(true)
                            .build()
            );
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            log.error("Error deleting application {}", applicationId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<Void>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("Application not found")
                            .errorMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        }
    }

}