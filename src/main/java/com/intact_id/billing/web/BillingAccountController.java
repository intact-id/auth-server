package com.intact_id.billing.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.billing.account.BillingAccounts;
import com.intact_id.billing.account.BillingAccounts.BillingModel;
import com.intact_id.billing.account.BillingAccounts.PaymentType;
import com.intact_id.billing.account.BillingAccounts.Setup;
import com.intact_id.billing.client.ResolvedServiceCharge;
import com.intact_id.config.web.DownstreamApi;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

import static com.intact_id.config.web.DownstreamApiResponses.respond;

/**
 * Billing from billing-service. Companies read their own account, wallet, prices and invoices; super admins act
 * for any company with ?companyId= and manage set-up, top-ups, prices and invoices under /admin.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billing")
@PreAuthorize("isAuthenticated()")
@DownstreamApi("Billing")
@Tag(name = "Billing", description = "Wallet, prices and invoices from billing-service.")
public class BillingAccountController {

    private static final String COMPANY_ID = "Super admins only: the company to act for";

    private final BillingAccounts billing;
    private final ObjectMapper objectMapper;

    public record ProfileChanges(BillingModel billingModel, PaymentType paymentType,
                                 @Pattern(regexp = "^[A-Z]{3}$") String currency, Boolean isActive) {
    }

    public record WalletCredit(@NotNull @Positive BigDecimal amount, String externalRef, String description) {
    }

    public record PerCallChanges(@PositiveOrZero BigDecimal pricePerCall, @PositiveOrZero Long freeQuota, Boolean isActive) {
    }

    public record NewServiceCharge(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String serviceCode,
                                   String name, @NotNull @PositiveOrZero BigDecimal price,
                                   @Pattern(regexp = "^[A-Z]{3}$") String currency) {
    }

    public record ServiceChargeChanges(String name, @PositiveOrZero BigDecimal price, Boolean isActive) {
    }

    // --- The company's own billing (super admins: ?companyId=) ---

    @GetMapping("/account")
    @Operation(summary = "Billing account", description = "client, billingProfile, wallet (balance) and perCallPricing; null where not set up. 404 when billing isn't set up.")
    public ResponseEntity<ApiResponse<JsonNode>> account(@Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(billing.account(company(companyId)));
    }

    @GetMapping("/wallet/history")
    @Operation(summary = "Wallet transactions")
    public ResponseEntity<ApiResponse<JsonNode>> walletHistory(@Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(billing.walletHistory(company(companyId)));
    }

    @GetMapping("/prices")
    @Operation(summary = "What each service costs this company", description = "The company's own price where set, otherwise the global one.")
    public ResponseEntity<ApiResponse<List<ResolvedServiceCharge>>> prices(@Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "OK", billing.prices(company(companyId)));
    }

    @GetMapping("/invoices")
    @Operation(summary = "Invoices")
    public ResponseEntity<ApiResponse<JsonNode>> invoices(@Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        return ok(billing.invoices(company(companyId)));
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "One invoice")
    public ResponseEntity<ApiResponse<JsonNode>> invoice(@PathVariable String id,
                                                         @Parameter(description = COMPANY_ID) @RequestParam(required = false) String companyId) {
        String owner = SecurityContextUtil.isSuperAdminAuthenticated() && blank(companyId) ? null : company(companyId);
        return ok(billing.invoice(owner, id));
    }

    // --- Super admin ---

    @PostMapping("/admin/setup")
    @Operation(summary = "Set a company's billing terms", description = "Creates the billing profile with the given model, payment type and currency, then the wallet (prepaid) and the default per-call price if pricePerCall is given. Only what is missing is created, so it is safe to repeat; change terms later with PATCH /admin/profile.")
    public ResponseEntity<ApiResponse<JsonNode>> setUp(@RequestParam String companyId, @RequestBody Setup setup) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Billing set up", billing.setUp(companyId, setup));
    }

    @PatchMapping("/admin/profile")
    @Operation(summary = "Change a company's billing model, payment type, currency or status")
    public ResponseEntity<ApiResponse<JsonNode>> updateProfile(@RequestParam String companyId, @Valid @RequestBody ProfileChanges changes) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Billing profile updated", billing.updateProfile(companyId, objectMapper.valueToTree(changes)));
    }

    @PostMapping("/admin/wallet/credit")
    @Operation(summary = "Top up a company's wallet")
    public ResponseEntity<ApiResponse<JsonNode>> creditWallet(@RequestParam String companyId, @Valid @RequestBody WalletCredit credit) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Wallet credited", billing.creditWallet(companyId, objectMapper.valueToTree(credit)));
    }

    @PatchMapping("/admin/per-call-pricing")
    @Operation(summary = "Change a company's default per-call price or free quota")
    public ResponseEntity<ApiResponse<JsonNode>> updatePerCallPricing(@RequestParam String companyId, @Valid @RequestBody PerCallChanges changes) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Per-call pricing updated", billing.updatePerCallPricing(companyId, objectMapper.valueToTree(changes)));
    }

    @GetMapping("/admin/service-charges")
    @Operation(summary = "Service prices", description = "Without companyId: the global prices. With it: that company's own prices.")
    public ResponseEntity<ApiResponse<JsonNode>> serviceCharges(@RequestParam(required = false) String companyId) {
        requireSuperAdmin();
        return ok(billing.serviceCharges(blank(companyId) ? null : companyId));
    }

    @PostMapping("/admin/service-charges")
    @Operation(summary = "Price a service", description = "serviceCode is the catalog code (e.g. LIVENESS). Without companyId the price is global.")
    public ResponseEntity<ApiResponse<JsonNode>> createServiceCharge(@RequestParam(required = false) String companyId,
                                                                     @Valid @RequestBody NewServiceCharge charge) {
        requireSuperAdmin();
        return respond(HttpStatus.CREATED, "Service price created",
                billing.createServiceCharge(blank(companyId) ? null : companyId, objectMapper.valueToTree(charge)));
    }

    @PatchMapping("/admin/service-charges/{id}")
    @Operation(summary = "Change a service price")
    public ResponseEntity<ApiResponse<JsonNode>> updateServiceCharge(@PathVariable String id, @Valid @RequestBody ServiceChargeChanges changes) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Service price updated", billing.updateServiceCharge(id, objectMapper.valueToTree(changes)));
    }

    @PostMapping("/admin/invoices/{id}/mark-paid")
    @Operation(summary = "Mark an invoice paid")
    public ResponseEntity<ApiResponse<JsonNode>> markInvoicePaid(@PathVariable String id) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Invoice marked paid", billing.markInvoicePaid(id));
    }

    @PostMapping("/admin/invoices/{id}/void")
    @Operation(summary = "Void an invoice")
    public ResponseEntity<ApiResponse<JsonNode>> voidInvoice(@PathVariable String id) {
        requireSuperAdmin();
        return respond(HttpStatus.OK, "Invoice voided", billing.voidInvoice(id));
    }

    private static String company(String companyId) {
        return SecurityContextUtil.resolveCompanyId(companyId);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireSuperAdmin() {
        if (!SecurityContextUtil.isSuperAdminAuthenticated()) {
            throw new ApplicationExceptions.IntactForbiddenException("Only Intact super admins manage billing");
        }
    }

    private static ResponseEntity<ApiResponse<JsonNode>> ok(JsonNode data) {
        return respond(HttpStatus.OK, "OK", data);
    }
}
