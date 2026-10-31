package com.intact_id.api.kyc;

import com.intact_id.db.model.Company;
import com.intact_id.dto.request.KycSessionRequestDto;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.dto.response.KycSessionResponseDto;
import com.intact_id.service.kyc.KycSessionService;
import com.intact_id.util.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/kyc/sessions")
@RequiredArgsConstructor
@Tag(name = "KYC Widget Sessions", description = "Create short-lived session tokens for the embeddable KYC widget")
public class KycSessionController {

    private final KycSessionService kycSessionService;

    @PostMapping
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(
        summary = "Create widget session token",
        description = "Exchange your API key for a short-lived (15 min) session token. " +
                      "Pass this token to your frontend — the widget uses it to authenticate " +
                      "without exposing your API secret to the browser."
    )
    public ResponseEntity<ApiResponse<KycSessionResponseDto>> createSession(
            @Valid @RequestBody KycSessionRequestDto request) {

        Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
        log.info("Creating widget session for company={} tier={} env={}",
                company.getId(), request.getTier(), request.getEnvironment());

        String token = kycSessionService.createSessionToken(
                company.getId(), request.getEnvironment(), request.getTier(), request.getExternalReference());

        KycSessionResponseDto response = KycSessionResponseDto.builder()
                .sessionToken(token)
                .expiresInSeconds(900)
                .tier(request.getTier())
                .environment(request.getEnvironment().name())
                .externalReference(request.getExternalReference())
                .build();

        return ResponseEntity.ok(ApiResponse.<KycSessionResponseDto>builder()
                .responseCode(200)
                .responseMessage("Session token created")
                .success(true)
                .data(response)
                .build());
    }
}
