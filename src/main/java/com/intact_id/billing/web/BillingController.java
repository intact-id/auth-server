package com.intact_id.billing.web;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.billing.BillingService;
import com.intact_id.accounts.SecurityContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/billing")
@Tag(name = "Billing (legacy)", description = "Deprecated: auth-server's old local billing. Use /api/v1/billing, which reads billing-service.")
@Deprecated
public class BillingController {

    /** Marks every response as deprecated (RFC 9745) and points to the replacement. */
    @ModelAttribute
    void deprecationHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Deprecation", "true");
        response.setHeader("Link", "</api/v1/billing>; rel=\"successor-version\"");
        log.warn("Deprecated billing endpoint called: {} {}", request.getMethod(), request.getRequestURI());
    }

    private final BillingService billingService;

    @PutMapping("/companies/{companyId}/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Create or update a company billing profile", deprecated = true, description = "Deprecated: use POST /api/v1/billing/admin/setup, PATCH /api/v1/billing/admin/profile.")
    public ResponseEntity<ApiResponse<CompanyBillingProfileResponseDto>> upsertProfile(
            @PathVariable String companyId,
            @Valid @RequestBody CompanyBillingProfileRequestDto request) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        CompanyBillingProfileResponseDto response = billingService.upsertCompanyBillingProfile(resolvedCompanyId, request);
        return ok("Billing profile updated successfully", response);
    }

    @GetMapping("/companies/{companyId}/profile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Get company billing profile", deprecated = true, description = "Deprecated: use GET /api/v1/billing/account?companyId=.")
    public ResponseEntity<ApiResponse<CompanyBillingProfileResponseDto>> getProfile(@PathVariable String companyId) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Billing profile retrieved successfully",
                billingService.getCompanyBillingProfile(resolvedCompanyId));
    }

    @PostMapping("/companies/{companyId}/wallet/topup")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Top up a prepaid company wallet", deprecated = true, description = "Deprecated: use POST /api/v1/billing/admin/wallet/credit.")
    public ResponseEntity<ApiResponse<WalletTransactionResponseDto>> topUpWallet(
            @PathVariable String companyId,
            @Valid @RequestBody WalletTopUpRequestDto request) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Wallet topped up successfully", billingService.topUpWallet(resolvedCompanyId, request));
    }

    @GetMapping("/companies/{companyId}/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Get company billing summary", deprecated = true, description = "Deprecated: use GET /api/v1/billing/account?companyId=.")
    public ResponseEntity<ApiResponse<BillingSummaryResponseDto>> getSummary(@PathVariable String companyId) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Billing summary retrieved successfully", billingService.getBillingSummary(resolvedCompanyId));
    }

    @GetMapping("/companies/{companyId}/wallet/transactions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Get company wallet transactions", deprecated = true, description = "Deprecated: use GET /api/v1/billing/wallet/history?companyId=.")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponseDto>>> getWalletTransactions(
            @PathVariable String companyId) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Wallet transactions retrieved successfully",
                billingService.getWalletTransactions(resolvedCompanyId));
    }

    @GetMapping("/companies/{companyId}/invoices")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Get company invoices", deprecated = true, description = "Deprecated: use GET /api/v1/billing/invoices?companyId=.")
    public ResponseEntity<ApiResponse<List<BillingInvoiceResponseDto>>> getInvoices(@PathVariable String companyId) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Invoices retrieved successfully", billingService.getInvoices(resolvedCompanyId));
    }

    @PostMapping("/companies/{companyId}/invoices/generate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Generate invoice for company", deprecated = true, description = "Deprecated: legacy local billing, no replacement yet (billing-service owns invoices).")
    public ResponseEntity<ApiResponse<BillingInvoiceResponseDto>> generateInvoice(
            @PathVariable String companyId,
            @Valid @RequestBody InvoiceGenerationRequestDto request) {
        String resolvedCompanyId = SecurityContextUtil.resolveCompanyId(companyId);
        return ok("Invoice generated successfully", billingService.generateInvoice(resolvedCompanyId, request));
    }

    @PostMapping("/invoices/{invoiceId}/send")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Deprecated
    @Operation(summary = "Send invoice email", deprecated = true, description = "Deprecated: legacy local billing, no replacement yet (billing-service owns invoices).")
    public ResponseEntity<ApiResponse<BillingInvoiceResponseDto>> sendInvoice(
            @PathVariable String invoiceId,
            @RequestBody(required = false) InvoiceEmailRequestDto request) {
        String recipient = request != null ? request.getRecipientEmail() : null;
        return ok("Invoice sent successfully", billingService.sendInvoice(invoiceId, recipient));
    }

    @GetMapping("/me/profile")
    @PreAuthorize("hasRole('COMPANY')")
    @Deprecated
    @Operation(summary = "Get billing profile for authenticated company", deprecated = true, description = "Deprecated: use GET /api/v1/billing/account.")
    public ResponseEntity<ApiResponse<CompanyBillingProfileResponseDto>> getMyProfile() {
        String companyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
        return ok("Billing profile retrieved successfully", billingService.getCompanyBillingProfile(companyId));
    }

    @GetMapping("/me/summary")
    @PreAuthorize("hasRole('COMPANY')")
    @Deprecated
    @Operation(summary = "Get billing summary for authenticated company", deprecated = true, description = "Deprecated: use GET /api/v1/billing/account.")
    public ResponseEntity<ApiResponse<BillingSummaryResponseDto>> getMySummary() {
        String companyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
        return ok("Billing summary retrieved successfully", billingService.getBillingSummary(companyId));
    }

    @GetMapping("/me/invoices")
    @PreAuthorize("hasRole('COMPANY')")
    @Deprecated
    @Operation(summary = "Get invoices for authenticated company", deprecated = true, description = "Deprecated: use GET /api/v1/billing/invoices.")
    public ResponseEntity<ApiResponse<List<BillingInvoiceResponseDto>>> getMyInvoices() {
        String companyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
        return ok("Invoices retrieved successfully", billingService.getInvoices(companyId));
    }

    @GetMapping("/me/wallet/transactions")
    @PreAuthorize("hasRole('COMPANY')")
    @Deprecated
    @Operation(summary = "Get wallet transactions for authenticated company", deprecated = true, description = "Deprecated: use GET /api/v1/billing/wallet/history.")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponseDto>>> getMyWalletTransactions() {
        String companyId = SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
        return ok("Wallet transactions retrieved successfully", billingService.getWalletTransactions(companyId));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .responseCode(HttpStatus.OK.value())
                .responseMessage(message)
                .success(true)
                .data(data)
                .build());
    }
}
