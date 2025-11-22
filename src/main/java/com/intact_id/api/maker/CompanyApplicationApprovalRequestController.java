package com.intact_id.api.maker;

import com.intact_id.dto.request.CompanyApplicationApprovalRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.ApplicationApprovalResponseDto;
import com.intact_id.service.companyApplicationService.CompanyApplicationService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @Author frank
 * email frank.mwangi@dadanadagroup.com
 * Created on 22/11/2025 12:56
 */

@Slf4j
@RestController
@RequestMapping("/api/company-approval/applications")
@RequiredArgsConstructor
@Tag(name = "Admin - Application Approvals", description = "Maker approval endpoints")
public class CompanyApplicationApprovalRequestController {
    private final CompanyApplicationService companyApplicationService;

    @PostMapping("/{applicationId}/maker-action")
    @Operation(summary = "Perform maker action on application")
    public ResponseEntity<ApiResponse<ApplicationApprovalResponseDto>> performMakerAction(
            @PathVariable String applicationId,
            @Valid @RequestBody CompanyApplicationApprovalRequestDto request
    ) {
        try {


            log.info("Maker action request for application: {}", applicationId);

            request.setApplicationId(applicationId);
            var response = companyApplicationService.performMakerAction(request);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("Company approval request has been created")
                            .success(true)
                            .data(response)
                            .build()
            );
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage("company application not found")
                            .success(false)
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        } catch (ApplicationExceptions.InvalidStatusTransitionException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage("Bad request")
                            .success(false)
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );
        } catch (ApplicationExceptions.DuplicateResourceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiResponse.<ApplicationApprovalResponseDto>builder()
                            .responseCode(HttpStatus.CONFLICT.value())
                            .responseMessage("there is a company application request with the same metadata as these request")
                            .success(false)
                            .responseMessage(e.getMessage())
                            .isInternalError(false)
                            .build()
            );

        }
    }}
