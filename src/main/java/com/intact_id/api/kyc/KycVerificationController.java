package com.intact_id.api.kyc;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.service.kyc.VerificationService;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC Verification", description = "Endpoints for submitting and managing KYC verifications")
public class KycVerificationController {

    private final VerificationService verificationService;

    @PostMapping(value = "/verify/{tier}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit KYC verification request", description = "Submit a new KYC verification with tier-specific requirements")
    public ResponseEntity<ApiResponse<KycVerificationResponseDto>> submitVerification(
            @PathVariable String tier,
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam String idNumber,
            @RequestParam(required = false) String idType,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String countryOfResidence,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String verificationType,
            @RequestParam(required = false) String webhookUrl,
            @RequestParam MultipartFile selfie,
            @RequestParam MultipartFile idFront,
            @RequestParam(required = false) MultipartFile idBack,
            @RequestParam(required = false) MultipartFile proofOfAddress,
            Authentication authentication) {

        try {
            // Get company from authentication context
            Company company = (Company) authentication.getPrincipal();

            log.info("Verification request from company: {}, tier: {}", company.getId(), tier);

            // Build request DTO
            KycVerificationRequestDto request = new KycVerificationRequestDto();
            request.setFirstName(firstName);
            request.setLastName(lastName);
            request.setDateOfBirth(dateOfBirth);
            request.setIdNumber(idNumber);
            request.setIdType(idType);
            request.setNationality(nationality);
            request.setCountryOfResidence(countryOfResidence);
            request.setPhone(phone);
            request.setEmail(email);
            request.setVerificationType(verificationType != null ? verificationType : "individual");
            request.setWebhookUrl(webhookUrl);
            request.setSelfie(selfie);
            request.setIdFront(idFront);
            request.setIdBack(idBack);
            request.setProofOfAddress(proofOfAddress);

            KycVerificationResponseDto response = verificationService.submitVerification(tier, request, company);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("Verification submitted successfully")
                            .success(true)
                            .data(response)
                            .build());

        } catch (ApplicationExceptions.IntactBadRequestException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        } catch (Exception e) {
            log.error("Error submitting verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Failed to submit verification")
                            .success(false)
                            .errorMessage(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/verify/{verificationId}")
    @Operation(summary = "Get verification status", description = "Retrieve the status and results of a verification request")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getVerificationStatus(
            @PathVariable String verificationId,
            Authentication authentication) {

        try {
            Company company = (Company) authentication.getPrincipal();

            VerificationStatusResponseDto response = verificationService.getVerificationStatus(
                    verificationId, company.getId());

            return ResponseEntity.ok(
                    ApiResponse.<VerificationStatusResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Verification status retrieved successfully")
                            .success(true)
                            .data(response)
                            .build());

        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<VerificationStatusResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping("/verifications")
    @Operation(summary = "List verifications", description = "Get a paginated list of verification requests with optional filters")
    public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> listVerifications(
            @Parameter(description = "Filter by status") @RequestParam(required = false) GlobalStatuses status,
            @Parameter(description = "Filter by tier") @RequestParam(required = false) String tier,
            @Parameter(description = "Filter from date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Filter to date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {

        Company company = (Company) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);

        Page<VerificationStatusResponseDto> verifications = verificationService.listVerifications(
                company.getId(), status, tier, fromDate, toDate, pageable);

        return ResponseEntity.ok(
                ApiResponse.<Page<VerificationStatusResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Verifications retrieved successfully")
                        .success(true)
                        .data(verifications)
                        .build());
    }
}
