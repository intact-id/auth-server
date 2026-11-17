package com.intact_id.billing.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.billing.topup.TopUpStatus;
import com.intact_id.billing.topup.TopUps;
import com.intact_id.billing.topup.WalletTopUp;
import com.intact_id.config.web.DownstreamApi;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.util.exception.ApplicationExceptions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Wallet top-ups. Companies report bank transfers with the receipt and follow their status; Intact super admins
 * review them, and an approved one credits the wallet.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billing")
@DownstreamApi("Billing")
@Tag(name = "Wallet top-ups", description = "Bank transfer top-ups with receipts, reviewed by Intact.")
public class TopUpController {

    private final TopUps topUps;

    @GetMapping("/top-ups/instructions")
    @PreAuthorize("hasAuthority('topups:view')")
    @Operation(summary = "How to top up", description = "The wallet's currency, where to send bank transfers, and whether the account can be topped up.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> instructions(@RequestParam(required = false) String companyId) {
        return ok(topUps.instructions(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @PostMapping(value = "/top-ups/bank", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('topups:manage')")
    @Operation(summary = "Report a bank transfer", description = "Amount in the wallet's currency, the bank's reference, the transfer date and the receipt (PDF or image, 10 MB). Intact reviews it before the wallet is credited.")
    public ResponseEntity<ApiResponse<WalletTopUp>> submitBank(@RequestParam(required = false) String companyId,
                                                               @RequestParam BigDecimal amount,
                                                               @RequestParam String reference,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidOn,
                                                               @RequestParam(required = false) String bankName,
                                                               @RequestParam(required = false) String note,
                                                               @RequestParam MultipartFile receipt) {
        WalletTopUp topUp = topUps.submitBankTransfer(SecurityContextUtil.resolveCompanyId(companyId),
                new TopUps.NewBankTopUp(amount, bankName, reference, paidOn, note), receipt, actor());
        return respond(HttpStatus.CREATED, "Top-up submitted for review", topUp);
    }

    @GetMapping("/top-ups")
    @PreAuthorize("hasAuthority('topups:view')")
    @Operation(summary = "Top-ups", description = "Newest first. Super admins without companyId see every company's; status filters (e.g. PENDING_REVIEW).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(@RequestParam(required = false) String companyId,
                                                                 @RequestParam(required = false) TopUpStatus status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        var result = topUps.list(scope(companyId), status, page, size);
        return ok(Map.of("content", result.getContent(), "totalElements", result.getTotalElements(), "totalPages", result.getTotalPages(),
                "waitingForReview", SecurityContextUtil.isSuperAdminAuthenticated() ? topUps.waitingForReview() : 0));
    }

    @GetMapping("/top-ups/{id}/receipt")
    @PreAuthorize("hasAuthority('topups:view')")
    @Operation(summary = "The receipt file")
    public ResponseEntity<InputStreamResource> receipt(@PathVariable String id) {
        WalletTopUp topUp = topUps.get(scope(null), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(topUp.getReceiptContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + topUp.getReference().replaceAll("[^A-Za-z0-9._-]", "_") + "\"")
                .body(new InputStreamResource(topUps.receipt(topUp)));
    }

    @PostMapping("/top-ups/{id}/cancel")
    @PreAuthorize("hasAuthority('topups:manage')")
    @Operation(summary = "Withdraw a top-up before it is reviewed")
    public ResponseEntity<ApiResponse<WalletTopUp>> cancel(@PathVariable String id) {
        return respond(HttpStatus.OK, "Top-up cancelled", topUps.cancel(scope(null), id));
    }

    // ----- Intact review -----

    @PostMapping("/admin/top-ups/{id}/approve")
    @PreAuthorize("hasAuthority('topups:manage-platform')")
    @Operation(summary = "Approve and credit the wallet", description = "amount: what the bank statement shows (default: the amount submitted).")
    public ResponseEntity<ApiResponse<WalletTopUp>> approve(@PathVariable String id, @RequestBody(required = false) Review review) {
        return respond(HttpStatus.OK, "Top-up approved and credited", topUps.approve(id, review == null ? null : review.amount(),
                review == null ? null : review.note(), actor()));
    }

    @PostMapping("/admin/top-ups/{id}/reject")
    @PreAuthorize("hasAuthority('topups:manage-platform')")
    @Operation(summary = "Reject a top-up", description = "The reason is shown to the company.")
    public ResponseEntity<ApiResponse<WalletTopUp>> reject(@PathVariable String id, @RequestBody Review review) {
        return respond(HttpStatus.OK, "Top-up rejected", topUps.reject(id, review.note(), actor()));
    }

    public record Review(BigDecimal amount, String note) {
    }

    /** Super admins: the company asked for, or all (null); everyone else: their own company. */
    private static String scope(String companyId) {
        if (SecurityContextUtil.isSuperAdminAuthenticated()) return companyId == null || companyId.isBlank() ? null : companyId;
        return SecurityContextUtil.resolveCompanyId(null);
    }

    private static String actor() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getUsername();
        throw new ApplicationExceptions.IntactForbiddenException("Top-ups are made by a signed-in person");
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return respond(HttpStatus.OK, "OK", data);
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
