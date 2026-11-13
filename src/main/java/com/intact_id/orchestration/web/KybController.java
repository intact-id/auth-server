package com.intact_id.orchestration.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.accounts.Company;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.KycSessionService;
import com.intact_id.orchestration.VerificationRequest;
import com.intact_id.orchestration.kyb.KybParties;
import com.intact_id.orchestration.kyb.KybSettings;
import com.intact_id.orchestration.kyb.KybSettingsService;
import com.intact_id.orchestration.kyb.PartyInput;
import com.intact_id.orchestration.kyb.PartyView;
import com.intact_id.orchestration.kyb.VerificationParty;
import com.intact_id.orchestration.model.VerificationRequestRepository;
import com.intact_id.orchestration.verification.VerificationService;
import com.intact_id.util.exception.ApplicationExceptions;
import com.intact_id.workflow.PinnedWorkflow;
import com.intact_id.workflow.SubjectType;
import com.intact_id.workflow.WorkflowRuntime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.intact_id.orchestration.web.WidgetResponses.respond;

/**
 * Business verification (KYB): submit a business with its directors and owners, send each person who must be
 * verified an invite link, and the owner-check settings (platform default and per-company override).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "KYB", description = "Verify businesses and the people behind them.")
public class KybController {

    private final VerificationService verificationService;
    private final KybParties kybParties;
    private final KybSettingsService settings;
    private final VerificationRequestRepository verifications;
    private final KycSessionService sessions;
    private final WorkflowRuntime workflowRuntime;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/api/v1/kyb/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COMPANY')")
    @Operation(summary = "Submit a business verification",
            description = "Runs a business workflow (workflow=<key>, else the company's default). parties is a JSON array of "
                    + "{firstName, lastName, dateOfBirth, nationality, email, phone, director, ownershipPercent}.")
    public ResponseEntity<ApiResponse<KycVerificationResponseDto>> submit(
            @RequestParam String companyName,
            @RequestParam(required = false) String tradingName,
            @RequestParam String registrationNumber,
            @Parameter(description = "ISO 3166 country code, e.g. KE") @RequestParam String country,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incorporationDate,
            @RequestParam(required = false) String registeredAddress,
            @RequestParam(required = false) String taxPin,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String workflow,
            @RequestParam(required = false) String externalReference,
            @RequestParam(required = false) String webhookUrl,
            @Parameter(description = "JSON array of directors and owners") @RequestParam(required = false) String parties,
            @RequestParam(required = false) MultipartFile certificateOfIncorporation,
            @RequestParam(required = false) MultipartFile cr12,
            @RequestParam(required = false) MultipartFile taxCertificate,
            @RequestParam(required = false) MultipartFile proofOfAddress) {

        Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("companyName", companyName);
        details.put("tradingName", tradingName);
        details.put("registrationNumber", registrationNumber);
        details.put("country", country == null ? null : country.trim().toUpperCase());
        details.put("incorporationDate", incorporationDate == null ? null : incorporationDate.toString());
        details.put("registeredAddress", registeredAddress);
        details.put("taxPin", taxPin);
        details.put("businessType", businessType);

        Map<String, MultipartFile> documents = new LinkedHashMap<>();
        if (certificateOfIncorporation != null) documents.put("certificate_of_incorporation", certificateOfIncorporation);
        if (cr12 != null) documents.put("cr12", cr12);
        if (taxCertificate != null) documents.put("tax_certificate", taxCertificate);
        if (proofOfAddress != null) documents.put("business_proof_of_address", proofOfAddress);

        KycVerificationRequestDto request = new KycVerificationRequestDto();
        request.setVerificationType("business");
        request.setBusinessDetails(details);
        request.setParties(parseParties(parties));
        request.setBusinessDocuments(documents);
        request.setWorkflow(workflow);
        request.setExternalReference(externalReference);
        request.setWebhookUrl(webhookUrl);

        KycVerificationResponseDto response = verificationService.submitVerification("workflow", request, company);
        return respond(HttpStatus.CREATED, "Business verification submitted", response);
    }

    @GetMapping("/api/v1/kyb/verifications/{id}/parties")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Directors and owners of a business verification, with their check status")
    public ResponseEntity<ApiResponse<List<PartyView>>> parties(@PathVariable String id) {
        VerificationRequest business = ownBusiness(id);
        return respond(HttpStatus.OK, "OK", kybParties.forVerification(business.getId()).stream().map(PartyView::of).toList());
    }

    @PostMapping("/api/v1/kyb/verifications/{id}/parties/{partyId}/invite")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Send a director or owner their verification link",
            description = "Emails the person (if an email was given) and returns the link; it works for 7 days. Sending again replaces the link.")
    public ResponseEntity<ApiResponse<Map<String, String>>> invite(@PathVariable String id, @PathVariable String partyId) {
        VerificationRequest business = ownBusiness(id);
        String link = kybParties.invite(business.getCompany().getId(), business.getId(), partyId);
        return respond(HttpStatus.OK, "Invite sent", Map.of("link", link));
    }

    @PostMapping("/api/public/kyb/invites/{token}/session")
    @Operation(summary = "Start a director's or owner's verification from their invite link",
            description = "Returns a widget session token; the verification is linked to their place on the business.")
    public ResponseEntity<ApiResponse<KycSessionResponseDto>> inviteSession(@PathVariable String token) {
        VerificationParty party = kybParties.fromInvite(token);
        VerificationRequest business = verifications.findByIdWithCompany(party.getVerificationId())
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Business verification not found"));
        Company company = business.getCompany();
        KybSettings rules = settings.effective(company.getId());

        PinnedWorkflow workflow = rules.personWorkflow() != null
                ? workflowRuntime.pin(company.getId(), rules.personWorkflow())
                : workflowRuntime.pinDefault(company.getId()).filter(w -> w.subjectType() == SubjectType.INDIVIDUAL).orElse(null);
        String tier = workflow == null ? "tier1" : KycSessionController.WORKFLOW_TIER;
        String reference = KybParties.PARTY_REFERENCE_PREFIX + party.getId();
        String sessionToken = sessions.createSessionToken(company.getId(), KycSessionService.displayName(company), tier, reference, workflow);

        return respond(HttpStatus.OK, "Session token created", KycSessionResponseDto.builder()
                .sessionToken(sessionToken)
                .expiresInSeconds(900)
                .tier(tier)
                .workflow(workflow == null ? null : workflow.key())
                .workflowVersion(workflow == null ? null : workflow.version())
                .externalReference(reference)
                .build());
    }

    // ----- settings -----

    @GetMapping("/api/v1/kyb/settings")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Owner-check settings", description = "The company's effective settings and the platform default.")
    public ResponseEntity<ApiResponse<Map<String, KybSettings>>> getSettings(@RequestParam(required = false) String companyId) {
        String company = companyId == null && SecurityContextUtil.isSuperAdminAuthenticated() ? null : SecurityContextUtil.resolveCompanyId(companyId);
        Map<String, KybSettings> body = new LinkedHashMap<>();
        body.put("platformDefault", settings.platformDefault());
        if (company != null) body.put("company", settings.effective(company));
        return respond(HttpStatus.OK, "OK", body);
    }

    @PutMapping("/api/v1/kyb/settings/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change the platform default", description = "Applies to every company without its own settings.")
    public ResponseEntity<ApiResponse<KybSettings>> updateDefault(@RequestBody KybSettings changes) {
        return respond(HttpStatus.OK, "Default updated", settings.update(null, changes));
    }

    @PutMapping("/api/v1/kyb/settings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Set the company's own settings", description = "Overrides the platform default for this company.")
    public ResponseEntity<ApiResponse<KybSettings>> updateCompany(@RequestParam(required = false) String companyId,
                                                                 @RequestBody KybSettings changes) {
        return respond(HttpStatus.OK, "Company settings saved", settings.update(SecurityContextUtil.resolveCompanyId(companyId), changes));
    }

    @DeleteMapping("/api/v1/kyb/settings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Go back to the platform default")
    public ResponseEntity<ApiResponse<KybSettings>> clearCompany(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Company uses the platform default", settings.clearOverride(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    /** A business verification the caller may see: their company's, or any for super admins. */
    private VerificationRequest ownBusiness(String id) {
        VerificationRequest business = verifications.findByIdWithCompany(id)
                .filter(v -> "business".equalsIgnoreCase(v.getVerificationType()))
                .orElseThrow(() -> new ApplicationExceptions.IntactResourceNotFoundException("Business verification not found"));
        if (!SecurityContextUtil.isSuperAdminAuthenticated()
                && !business.getCompany().getId().equals(SecurityContextUtil.getAuthenticatedCompanyFromAny().getId())) {
            throw new ApplicationExceptions.IntactResourceNotFoundException("Business verification not found");
        }
        return business;
    }

    private List<PartyInput> parseParties(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<PartyInput>>() { });
        } catch (Exception e) {
            throw new ApplicationExceptions.IntactBadRequestException("parties must be a JSON array of directors and owners: " + e.getMessage());
        }
    }
}
