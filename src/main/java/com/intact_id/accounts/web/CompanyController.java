package com.intact_id.accounts.web;

import com.intact_id.constant.enums.GlobalStatuses;
import com.intact_id.accounts.Company;
import com.intact_id.notifications.CompanyCommunicationDetails;
import com.intact_id.accounts.CompanyCredentials;
import com.intact_id.accounts.CompanyCredentialsRepository;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.accounts.CompanyService;
import com.intact_id.notifications.WebhookDeliverySummary;
import com.intact_id.notifications.WebhookDeliveryView;
import com.intact_id.notifications.WebhookService;
import com.intact_id.accounts.SecurityContextUtil;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Company Management", description = "Manage company details")
public class CompanyController {

    private final CompanyService companyService;
    private final WebhookService webhookService;
    private final CompanyCredentialsRepository companyCredentialsRepository;

    @GetMapping("/{id}")
    @Operation(summary = "Get company by ID")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> getCompanyById(@PathVariable String id) {
        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            Company company = companyService.getCompanyById(resolvedCompanyId);
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company retrieved successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping("/me")
    @Operation(summary = "Get current company details")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> getCurrentCompany(
            @RequestParam(required = false) String companyId) {

        return getCompanyById(SecurityContextUtil.resolveCompanyId(companyId));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update company details")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> updateCompany(
            @PathVariable String id,
            @Valid @RequestBody CompanyUpdateRequestDto request) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            Company company = companyService.updateCompany(resolvedCompanyId, request);
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company updated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping
    @Operation(summary = "Get all companies with filters (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CompanyResponseDto>>> getAllCompanies(
            @RequestParam(required = false) GlobalStatuses status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String businessType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (!SecurityContextUtil.isSuperAdminAuthenticated()) {
            Company company = SecurityContextUtil.getAuthenticatedCompanyFromAny();
            CompanyResponseDto response = mapToDto(company);
            Page<CompanyResponseDto> pageResponse = new PageImpl<>(java.util.List.of(response));

            return ResponseEntity.ok(
                    ApiResponse.<Page<CompanyResponseDto>>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Companies retrieved successfully")
                            .success(true)
                            .data(pageResponse)
                            .build());
        }

        log.info("Fetching companies - Status: {}, Country: {}, BusinessType: {}", status, country,
                businessType);

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                size);

        Page<Company> companies = companyService.getAllCompanies(status, country, businessType, pageable);
        Page<CompanyResponseDto> response = companies.map(this::mapToDto);

        return ResponseEntity.ok(
                ApiResponse.<Page<CompanyResponseDto>>builder()
                        .responseCode(HttpStatus.OK.value())
                        .responseMessage("Companies retrieved successfully")
                        .success(true)
                        .data(response)
                        .build());
    }

    // ========== Webhook/Communication Endpoints ==========

    @GetMapping("/{id}/communication")
    @Operation(summary = "Get company communication details")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    public ResponseEntity<ApiResponse<CompanyCommunicationResponseDto>> getCompanyCommunication(
            @PathVariable String id) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            CompanyCommunicationDetails communication = companyService.getCompanyCommunication(resolvedCompanyId);
            CompanyCommunicationResponseDto response = mapCommunicationToDto(communication);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Communication details retrieved successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @GetMapping("/{id}/communication/webhook/deliveries")
    @Operation(summary = "List webhook deliveries",
            description = "A company's webhook delivery attempts, newest first. Payloads and receiver responses are not included.")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    public ResponseEntity<ApiResponse<Page<WebhookDeliveryView>>> getWebhookDeliveries(
            @PathVariable String id,
            @Parameter(description = "PENDING, DELIVERED, FAILED or RETRYING") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        try {
            Page<WebhookDeliveryView> deliveries = webhookService.listDeliveries(resolvedCompanyId, status, pageable);
            return ResponseEntity.ok(ApiResponse.<Page<WebhookDeliveryView>>builder()
                    .responseCode(HttpStatus.OK.value())
                    .responseMessage("Webhook deliveries retrieved successfully")
                    .success(true)
                    .data(deliveries)
                    .build());
        } catch (ApplicationExceptions.InvalidRequestException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<Page<WebhookDeliveryView>>builder()
                    .responseCode(HttpStatus.BAD_REQUEST.value())
                    .responseMessage(e.getMessage())
                    .success(false)
                    .build());
        }
    }

    @GetMapping("/{id}/communication/webhook/deliveries/summary")
    @Operation(summary = "Summarise webhook deliveries",
            description = "Delivery counts by status for the last `hours` hours (default 24, maximum 720).")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    public ResponseEntity<ApiResponse<WebhookDeliverySummary>> getWebhookDeliverySummary(
            @PathVariable String id,
            @RequestParam(defaultValue = "24") int hours) {

        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
        LocalDateTime since = LocalDateTime.now().minusHours(Math.min(Math.max(hours, 1), 720));
        return ResponseEntity.ok(ApiResponse.<WebhookDeliverySummary>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage("Webhook delivery summary retrieved successfully")
                .success(true)
                .data(webhookService.summarizeDeliveries(resolvedCompanyId, since))
                .build());
    }

    @PutMapping("/{id}/communication/webhook")
    @Operation(summary = "Update webhook settings")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyCommunicationResponseDto>> updateWebhookSettings(
            @PathVariable String id,
            @Valid @RequestBody WebhookSettingsRequestDto request) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            CompanyCommunicationDetails communication = companyService.updateWebhookSettings(resolvedCompanyId, request);
            CompanyCommunicationResponseDto response = mapCommunicationToDto(communication);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Webhook settings updated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/{id}/communication/webhook/test")
    @Operation(summary = "Test webhook endpoint")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testWebhook(@PathVariable String id) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            CompanyCommunicationDetails communication = companyService.getCompanyCommunication(resolvedCompanyId);

            if (communication.getWebhookUrl() == null || communication.getWebhookUrl().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.<String>builder()
                                .responseCode(HttpStatus.BAD_REQUEST.value())
                                .responseMessage("No webhook URL configured")
                                .success(false)
                                .build());
            }

            // Send test webhook
            boolean success = webhookService.sendTestWebhook(communication);

            if (success) {
                return ResponseEntity.ok(
                        ApiResponse.<String>builder()
                                .responseCode(HttpStatus.OK.value())
                                .responseMessage("Test webhook sent successfully")
                                .success(true)
                                .data("Webhook test completed. Check your endpoint logs.")
                                .build());
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                        ApiResponse.<String>builder()
                                .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .responseMessage("Failed to send test webhook")
                                .success(false)
                                .build());
            }
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PutMapping("/{id}/communication/notification-settings")
    @Operation(summary = "Update applicant notification settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CompanyCommunicationResponseDto>> updateApplicantNotifications(
            @PathVariable String id,
            @RequestBody java.util.Map<String, Boolean> flags) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            boolean onApproval    = Boolean.TRUE.equals(flags.getOrDefault("notifyApplicantOnApproval", true));
            boolean onRejection   = Boolean.TRUE.equals(flags.getOrDefault("notifyApplicantOnRejection", true));
            boolean onPending     = Boolean.TRUE.equals(flags.getOrDefault("notifyApplicantOnPendingReview", false));

            CompanyCommunicationDetails communication =
                    companyService.updateApplicantNotifications(resolvedCompanyId, onApproval, onRejection, onPending);

            return ResponseEntity.ok(ApiResponse.<CompanyCommunicationResponseDto>builder()
                    .responseCode(200)
                    .responseMessage("Applicant notification settings updated")
                    .success(true)
                    .data(mapCommunicationToDto(communication))
                    .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(404).responseMessage(e.getMessage()).success(false).build());
        }
    }

    @PostMapping("/{id}/communication/webhook/rotate-secret")
    @Operation(summary = "Rotate webhook signing secret")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyCommunicationResponseDto>> rotateWebhookSecret(@PathVariable String id) {
        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            CompanyCommunicationDetails communication = companyService.rotateWebhookSecret(resolvedCompanyId);
            // Return the full (unmasked) secret once — consumer must copy it now
            CompanyCommunicationResponseDto response = mapCommunicationToDto(communication);
            response.setWebhookSecret(communication.getWebhookSecret());

            return ResponseEntity.ok(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Webhook secret rotated. Copy it now — it will not be shown again in full.")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyCommunicationResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/{id}/credentials/suspend")
    @Operation(summary = "Suspend company credentials (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> suspendCredentials(
            @PathVariable String id) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            companyService.suspendCredentials(resolvedCompanyId);

            return ResponseEntity.ok(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company credentials suspended successfully")
                            .success(true)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/{id}/credentials/activate")
    @Operation(summary = "Activate company credentials (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> activateCredentials(
            @PathVariable String id) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            companyService.activateCredentials(resolvedCompanyId);

            return ResponseEntity.ok(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company credentials activated successfully")
                            .success(true)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    // ========== Company Management Endpoints ==========

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete company (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteCompany(@PathVariable String id) {
        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            companyService.deleteCompany(resolvedCompanyId);

            return ResponseEntity.ok(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company deleted successfully")
                            .success(true)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponse.<String>builder()
                            .responseCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .responseMessage("Failed to delete company: " + e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend company (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> suspendCompany(
            @PathVariable String id,
            @RequestParam(required = false) String reason) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            Company company = companyService.suspendCompany(resolvedCompanyId, "admin",
                    reason != null ? reason : "Suspended by admin");
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company suspended successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        } catch (ApplicationExceptions.InvalidActionException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate company (Admin)")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CompanyResponseDto>> activateCompany(@PathVariable String id) {

        try {
            String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(id);
            Company company = companyService.activateCompany(resolvedCompanyId, "admin");
            CompanyResponseDto response = mapToDto(company);

            return ResponseEntity.ok(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.OK.value())
                            .responseMessage("Company activated successfully")
                            .success(true)
                            .data(response)
                            .build());
        } catch (ApplicationExceptions.IntactCompanyNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.NOT_FOUND.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        } catch (ApplicationExceptions.InvalidActionException e) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.<CompanyResponseDto>builder()
                            .responseCode(HttpStatus.BAD_REQUEST.value())
                            .responseMessage(e.getMessage())
                            .success(false)
                            .build());
        }
    }

    // ========== Rate Limit Endpoints ==========

    @GetMapping("/{id}/rate-limits")
    @Operation(summary = "Get rate limits for a company")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RateLimitsResponseDto>> getRateLimits(@PathVariable String id) {
        String companyId = SecurityContextUtil.resolveCompanyId(id);
        Company company = companyService.getCompanyById(companyId);
        CompanyCredentials creds = companyCredentialsRepository.findByCompanyId(companyId).orElse(null);

        return ResponseEntity.ok(ApiResponse.<RateLimitsResponseDto>builder()
                .success(true).responseCode(200)
                .data(toRateLimitsDto(company, creds))
                .build());
    }

    @PutMapping("/{id}/rate-limits")
    @Operation(summary = "Configure rate limits for a company (SUPER_ADMIN only)")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RateLimitsResponseDto>> updateRateLimits(
            @PathVariable String id,
            @RequestBody RateLimitsRequestDto request) {

        String companyId = SecurityContextUtil.resolveCompanyId(id);
        Company company = companyService.getCompanyById(companyId);
        CompanyCredentials creds = companyCredentialsRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ApplicationExceptions.IntactCompanyNotFoundException(
                        "No credentials found for company: " + companyId));

        if (request.getPerHour() != null) creds.setRateLimitPerHour(request.getPerHour() < 0 ? null : request.getPerHour());
        if (request.getPerDay() != null) creds.setRateLimitPerDay(request.getPerDay() < 0 ? null : request.getPerDay());
        if (request.getPerMonth() != null) creds.setRateLimitPerMonth(request.getPerMonth() < 0 ? null : request.getPerMonth());

        companyCredentialsRepository.save(creds);
        log.info("Rate limits updated for company {} by {}", companyId, SecurityContextUtil.getAuthenticatedUser().getEmail());

        return ResponseEntity.ok(ApiResponse.<RateLimitsResponseDto>builder()
                .success(true).responseCode(200)
                .responseMessage("Rate limits updated successfully")
                .data(toRateLimitsDto(company, creds))
                .build());
    }

    // ========== Helper Methods ==========

    private CompanyResponseDto mapToDto(Company company) {
        return CompanyResponseDto.builder()
                .id(company.getId())
                .companyIdentifierCode(company.getCompanyIdentifierCode())
                .legalName(company.getLegalName())
                .tradingName(company.getTradingName())
                .registrationNumber(company.getRegistrationNumber())
                .country(company.getCountry())
                .businessType(company.getBusinessType())
                .status(company.getStatus().name())
                .approvedAt(company.getApprovedAt())
                .createdAt(company.getCreatedAt())
                .build();
    }

    /** Shows first 10 and last 4 chars: whsec_ab12...ef34 */
    private String maskSecret(String secret) {
        if (secret == null || secret.length() < 15) return secret;
        return secret.substring(0, 10) + "..." + secret.substring(secret.length() - 4);
    }

    private RateLimitsResponseDto toRateLimitsDto(Company company, CompanyCredentials creds) {
        return RateLimitsResponseDto.builder()
                .companyId(company.getId())
                .companyName(company.getLegalName())
                .perHour(creds != null ? creds.getRateLimitPerHour() : null)
                .perDay(creds != null ? creds.getRateLimitPerDay() : null)
                .perMonth(creds != null ? creds.getRateLimitPerMonth() : null)
                .totalRequests(creds != null ? creds.getTotalRequests() : 0L)
                .totalSuccessful(creds != null ? creds.getTotalSuccessfulRequests() : 0L)
                .totalFailed(creds != null ? creds.getTotalFailedRequests() : 0L)
                .build();
    }

    private CompanyCommunicationResponseDto mapCommunicationToDto(CompanyCommunicationDetails communication) {
        return CompanyCommunicationResponseDto.builder()
                .id(communication.getId())
                .email(communication.getEmail())
                .phone(communication.getPhone())
                .website(communication.getWebsite())
                .secondaryEmail(communication.getSecondaryEmail())
                .secondaryPhone(communication.getSecondaryPhone())
                .supportEmail(communication.getSupportEmail())
                .billingEmail(communication.getBillingEmail())
                .webhookUrl(communication.getWebhookUrl())
                .webhookSecret(maskSecret(communication.getWebhookSecret()))
                .webhookEnabled(communication.getWebhookEnabled())
                .emailNotifications(communication.getEmailNotifications())
                .smsNotifications(communication.getSmsNotifications())
                .notificationSettings(communication.getNotificationSettings())
                .payloadFields(communication.getPayloadFields())
                .build();
    }
}
