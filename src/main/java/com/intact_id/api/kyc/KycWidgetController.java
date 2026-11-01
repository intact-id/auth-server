package com.intact_id.api.kyc;

import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.constant.enums.kyc.WidgetChannel;
import com.intact_id.db.model.Company;
import com.intact_id.dto.request.KycVerificationRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.KycVerificationResponseDto;
import com.intact_id.dto.response.VerificationStatusResponseDto;
import com.intact_id.security.authentication.WidgetSessionContext;
import com.intact_id.service.kyc.VerificationService;
import com.intact_id.util.SecurityContextUtil;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc/widget")
@RequiredArgsConstructor
@Tag(name = "KYC Widget", description = "Endpoints consumed exclusively by the embeddable KYC iframe widget")
public class KycWidgetController {

    private final VerificationService verificationService;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Submit KYC verification from widget",
               description = "Authenticated via X-Session-Token. Tier and environment are derived from the session token.")
    public ResponseEntity<ApiResponse<KycVerificationResponseDto>> submitVerification(
            @RequestParam String firstName,
            @RequestParam String lastName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam String idNumber,
            @RequestParam(required = false) String idType,
            @RequestParam(required = false) String nationality,
            @RequestParam(required = false) String countryOfResidence,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam MultipartFile selfie,
            @RequestParam MultipartFile idFront,
            @RequestParam(required = false) MultipartFile idBack,
            @RequestParam(required = false) MultipartFile proofOfAddress,
            @RequestParam(required = false) List<MultipartFile> livenessFrames,
            @RequestParam(required = false) String livenessLabels,
            HttpServletRequest httpRequest) {

        // Phone tokens are scoped to one draft and must not start new verifications
        WidgetSessionContext context = (WidgetSessionContext) httpRequest.getAttribute(WidgetSessionContext.ATTRIBUTE);
        if (context != null && context.channel() == WidgetChannel.HANDOFF) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(HttpStatus.FORBIDDEN.value())
                            .responseMessage("Use the draft submit endpoint from a phone session")
                            .success(false)
                            .build());
        }

        try {
            Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
            ApiKeyEnvironment environment = (ApiKeyEnvironment) httpRequest.getAttribute("widget.environment");
            String tier = (String) httpRequest.getAttribute("widget.tier");

            KycVerificationRequestDto dto = new KycVerificationRequestDto();
            dto.setFirstName(firstName);
            dto.setLastName(lastName);
            dto.setDateOfBirth(dateOfBirth);
            dto.setIdNumber(idNumber);
            dto.setIdType(idType);
            dto.setNationality(nationality);
            dto.setCountryOfResidence(countryOfResidence);
            dto.setPhone(phone);
            dto.setEmail(email);
            dto.setVerificationType("individual");
            dto.setExternalReference((String) httpRequest.getAttribute("widget.externalReference"));
            dto.setSelfie(selfie);
            dto.setIdFront(idFront);
            dto.setIdBack(idBack);
            dto.setProofOfAddress(proofOfAddress);
            dto.setLivenessFrames(livenessFrames);
            dto.setLivenessLabels(livenessLabels);

            KycVerificationResponseDto result = verificationService.submitVerification(tier, dto, company, environment);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(HttpStatus.CREATED.value())
                            .responseMessage("Verification submitted")
                            .success(true)
                            .data(result)
                            .build());

        } catch (ApplicationExceptions.IntactBadRequestException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(400)
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        } catch (Exception e) {
            log.error("Widget verification error", e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.<KycVerificationResponseDto>builder()
                            .responseCode(500)
                            .responseMessage("Verification failed")
                            .success(false)
                            .build());
        }
    }

    @GetMapping("/verify/{verificationId}")
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Poll verification status from widget")
    public ResponseEntity<ApiResponse<VerificationStatusResponseDto>> getStatus(
            @PathVariable String verificationId,
            HttpServletRequest httpRequest) {

        try {
            Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
            ApiKeyEnvironment environment = (ApiKeyEnvironment) httpRequest.getAttribute("widget.environment");

            VerificationStatusResponseDto status = verificationService.getVerificationStatus(
                    verificationId, company.getId(), environment);

            return ResponseEntity.ok(ApiResponse.<VerificationStatusResponseDto>builder()
                    .responseCode(200)
                    .responseMessage("OK")
                    .success(true)
                    .data(status)
                    .build());

        } catch (Exception e) {
            log.error("Widget status error", e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.<VerificationStatusResponseDto>builder()
                            .responseCode(500)
                            .responseMessage("Failed to fetch status")
                            .success(false)
                            .build());
        }
    }
}
