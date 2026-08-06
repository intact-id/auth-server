package com.intact_id.api.kyc;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.db.model.Company;
import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.service.kyc.VerificationService;
import com.intact_id.util.SecurityContextUtil;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
        @PreAuthorize("hasRole('COMPANY')")
        @Operation(summary = "Submit KYC verification request (PROD)", description = "Submit a new KYC verification with tier-specific requirements. "
                        + "Preferred auth: Bearer access token from POST /api/v1/auth/token. "
                        + "Direct X-API-Key/X-API-Secret auth still works here but is deprecated — see ApiKeyAuthFilter.")
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

                return submitVerificationInternal(ApiKeyEnvironment.PROD, tier, firstName, lastName, dateOfBirth,
                                idNumber, idType, nationality, countryOfResidence, phone, email, verificationType,
                                webhookUrl, selfie, idFront, idBack, proofOfAddress, authentication);
        }

        @PostMapping(value = "/dev/verify/{tier}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('COMPANY')")
        @Operation(summary = "Submit KYC verification request (DEV)", description = "Submit a new DEV KYC verification with tier-specific requirements. "
                        + "Preferred auth: Bearer access token from POST /api/v1/auth/token. "
                        + "Direct X-API-Key/X-API-Secret auth still works here but is deprecated — see ApiKeyAuthFilter.")
        public ResponseEntity<ApiResponse<KycVerificationResponseDto>> submitVerificationDev(
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

                return submitVerificationInternal(ApiKeyEnvironment.DEV, tier, firstName, lastName, dateOfBirth,
                                idNumber, idType, nationality, countryOfResidence, phone, email, verificationType,
                                webhookUrl, selfie, idFront, idBack, proofOfAddress, authentication);
        }

        private ResponseEntity<ApiResponse<KycVerificationResponseDto>> submitVerificationInternal(
                        ApiKeyEnvironment environment,
                        String tier,
                        String firstName,
                        String lastName,
                        LocalDate dateOfBirth,
                        String idNumber,
                        String idType,
                        String nationality,
                        String countryOfResidence,
                        String phone,
                        String email,
                        String verificationType,
                        String webhookUrl,
                        MultipartFile selfie,
                        MultipartFile idFront,
                        MultipartFile idBack,
                        MultipartFile proofOfAddress,
                        Authentication authentication) {

                try {
                        // Resolve tenant company for either API-key company auth or JWT user auth
                        Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();

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

                        KycVerificationResponseDto response = verificationService.submitVerification(tier, request,
                                        company, environment);

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

        @PostMapping("/verifications/{verificationId}/release")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        @Operation(summary = "Release a held verification", description = "Manually release a single ON_HOLD verification back to SUBMITTED so the scheduler will retry it")
        public ResponseEntity<ApiResponse<Void>> releaseHeldVerification(
                        @PathVariable String verificationId) {
                try {
                        verificationService.releaseHeldVerification(verificationId);
                        return ResponseEntity.ok(ApiResponse.<Void>builder()
                                        .responseCode(HttpStatus.OK.value())
                                        .responseMessage("Verification released successfully")
                                        .success(true)
                                        .build());
                } catch (com.intact_id.util.exception.ApplicationExceptions.IntactResourceNotFoundException e) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<Void>builder()
                                        .responseCode(HttpStatus.NOT_FOUND.value())
                                        .responseMessage(e.getMessage())
                                        .success(false)
                                        .build());
                } catch (com.intact_id.util.exception.ApplicationExceptions.InvalidActionException e) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.<Void>builder()
                                        .responseCode(HttpStatus.BAD_REQUEST.value())
                                        .responseMessage(e.getMessage())
                                        .success(false)
                                        .build());
                }
        }

        @PostMapping("/verifications/release-held")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
        @Operation(summary = "Release all held verifications", description = "Release all ON_HOLD verifications back to SUBMITTED. Optionally scope to a single company.")
        public ResponseEntity<ApiResponse<Void>> releaseAllHeld(
                        @Parameter(description = "Release only for this company (omit to release all)") @RequestParam(required = false) String companyId) {
                int released = verificationService.releaseAllHeldForCompany(companyId);
                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .responseCode(HttpStatus.OK.value())
                                .responseMessage(released + " verification(s) released")
                                .success(true)
                                .build());
        }

        @GetMapping("/verify/{verificationId}")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "Get verification status", description = "Retrieve the status and results of a verification request")
        public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getVerificationStatus(
                        @PathVariable String verificationId,
                        Authentication authentication) {

                try {
                        String companyId = SecurityContextUtil.isSuperAdminAuthenticated()
                                        ? null
                                        : SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();

                        VerificationStatusResponseDto response = verificationService.getVerificationStatus(
                                        verificationId, companyId, ApiKeyEnvironment.PROD);

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
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "List verifications", description = "Get a paginated list of verification requests with optional filters")
        public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> listVerifications(
                        @Parameter(description = "Filter by status") @RequestParam(required = false) GlobalStatuses status,
                        @Parameter(description = "Filter by tier") @RequestParam(required = false) String tier,
                        @Parameter(description = "Filter from date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
                        @Parameter(description = "Filter to date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
                        @Parameter(description = "Search by verification/customer/company fields") @RequestParam(required = false) String search,
                        @Parameter(description = "Filter by company (Admin only)") @RequestParam(required = false) String companyId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Authentication authentication) {

                String resolvedCompanyId;
                if (SecurityContextUtil.isSuperAdminAuthenticated()) {
                        resolvedCompanyId = (companyId != null && !companyId.isBlank())
                                        ? SecurityContextUtil.resolveCompanyId(companyId)
                                        : null;
                } else {
                        resolvedCompanyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
                }

                Pageable pageable = PageRequest.of(page, size);

                Page<VerificationStatusResponseDto> verifications = verificationService.listVerifications(
                                resolvedCompanyId, ApiKeyEnvironment.PROD, status, tier, fromDate, toDate, search, pageable);

                return ResponseEntity.ok(
                                ApiResponse.<Page<VerificationStatusResponseDto>>builder()
                                                .responseCode(HttpStatus.OK.value())
                                                .responseMessage("Verifications retrieved successfully")
                                                .success(true)
                                                .data(verifications)
                                                .build());
        }

        @GetMapping("/prod/verify/{verificationId}")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "Get PROD verification status", description = "Retrieve PROD verification status")
        public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getVerificationStatusProd(
                        @PathVariable String verificationId,
                        Authentication authentication) {

                try {
                        String companyId = SecurityContextUtil.isSuperAdminAuthenticated()
                                        ? null
                                        : SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();

                        VerificationStatusResponseDto response = verificationService.getVerificationStatus(
                                        verificationId, companyId, ApiKeyEnvironment.PROD);

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

        @GetMapping("/prod/verifications")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "List PROD verifications", description = "Get a paginated list of PROD verification requests with optional filters")
        public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> listVerificationsProd(
                        @Parameter(description = "Filter by status") @RequestParam(required = false) GlobalStatuses status,
                        @Parameter(description = "Filter by tier") @RequestParam(required = false) String tier,
                        @Parameter(description = "Filter from date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
                        @Parameter(description = "Filter to date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
                        @Parameter(description = "Search by verification/customer/company fields") @RequestParam(required = false) String search,
                        @Parameter(description = "Filter by company (Admin only)") @RequestParam(required = false) String companyId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Authentication authentication) {

                String resolvedCompanyId;
                if (SecurityContextUtil.isSuperAdminAuthenticated()) {
                        resolvedCompanyId = (companyId != null && !companyId.isBlank())
                                        ? SecurityContextUtil.resolveCompanyId(companyId)
                                        : null;
                } else {
                        resolvedCompanyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
                }

                Pageable pageable = PageRequest.of(page, size);

                Page<VerificationStatusResponseDto> verifications = verificationService.listVerifications(
                                resolvedCompanyId, ApiKeyEnvironment.PROD, status, tier, fromDate, toDate, search, pageable);

                return ResponseEntity.ok(
                                ApiResponse.<Page<VerificationStatusResponseDto>>builder()
                                                .responseCode(HttpStatus.OK.value())
                                                .responseMessage("Verifications retrieved successfully")
                                                .success(true)
                                                .data(verifications)
                                                .build());
        }

        @GetMapping("/dev/verify/{verificationId}")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "Get DEV verification status", description = "Retrieve DEV verification status")
        public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getVerificationStatusDev(
                        @PathVariable String verificationId,
                        Authentication authentication) {

                try {
                        String companyId = SecurityContextUtil.isSuperAdminAuthenticated()
                                        ? null
                                        : SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();

                        VerificationStatusResponseDto response = verificationService.getVerificationStatus(
                                        verificationId, companyId, ApiKeyEnvironment.DEV);

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

        @GetMapping("/dev/verifications")
        @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
        @Operation(summary = "List DEV verifications", description = "Get a paginated list of DEV verification requests with optional filters")
        public ResponseEntity<ApiResponse<Page<VerificationStatusResponseDto>>> listVerificationsDev(
                        @Parameter(description = "Filter by status") @RequestParam(required = false) GlobalStatuses status,
                        @Parameter(description = "Filter by tier") @RequestParam(required = false) String tier,
                        @Parameter(description = "Filter from date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
                        @Parameter(description = "Filter to date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
                        @Parameter(description = "Search by verification/customer/company fields") @RequestParam(required = false) String search,
                        @Parameter(description = "Filter by company (Admin only)") @RequestParam(required = false) String companyId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size,
                        Authentication authentication) {

                String resolvedCompanyId;
                if (SecurityContextUtil.isSuperAdminAuthenticated()) {
                        resolvedCompanyId = (companyId != null && !companyId.isBlank())
                                        ? SecurityContextUtil.resolveCompanyId(companyId)
                                        : null;
                } else {
                        resolvedCompanyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
                }

                Pageable pageable = PageRequest.of(page, size);

                Page<VerificationStatusResponseDto> verifications = verificationService.listVerifications(
                                resolvedCompanyId, ApiKeyEnvironment.DEV, status, tier, fromDate, toDate, search, pageable);

                return ResponseEntity.ok(
                                ApiResponse.<Page<VerificationStatusResponseDto>>builder()
                                                .responseCode(HttpStatus.OK.value())
                                                .responseMessage("Verifications retrieved successfully")
                                                .success(true)
                                                .data(verifications)
                                                .build());
        }

}
