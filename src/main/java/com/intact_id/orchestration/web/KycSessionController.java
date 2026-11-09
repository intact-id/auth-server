package com.intact_id.orchestration.web;

import com.intact_id.accounts.Company;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.constant.enums.ApiKeyEnvironment;
import com.intact_id.orchestration.KycSessionService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.PinnedWorkflow;
import com.intact_id.workflow.WorkflowRuntime;
import com.intact_id.accounts.SecurityContextUtil;
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

    // Stored as the tier of workflow sessions; the workflow decides what runs
    public static final String WORKFLOW_TIER = "workflow";

    private final KycSessionService kycSessionService;
    private final WorkflowRuntime workflowRuntime;

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

        PinnedWorkflow workflow = request.getWorkflow() == null || request.getWorkflow().isBlank() ? null
                : workflowRuntime.pin(company.getId(), request.getWorkflow().trim(), request.getEnvironment() == ApiKeyEnvironment.PROD);
        String tier = request.getTier();
        if (workflow == null && (tier == null || tier.isBlank())) {
            throw new ApplicationExceptions.IntactBadRequestException("tier is required (tier1, tier2, tier3) unless a workflow is given");
        }
        if (tier == null || tier.isBlank()) {
            tier = WORKFLOW_TIER;
        }
        String token = kycSessionService.createSessionToken(
                company.getId(), KycSessionService.displayName(company), request.getEnvironment(),
                tier, request.getExternalReference(), workflow);

        KycSessionResponseDto response = KycSessionResponseDto.builder()
                .sessionToken(token)
                .expiresInSeconds(900)
                .tier(tier)
                .workflow(workflow == null ? null : workflow.key())
                .workflowVersion(workflow == null ? null : workflow.version())
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
