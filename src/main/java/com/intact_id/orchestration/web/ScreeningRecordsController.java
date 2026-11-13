package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.verification.ScreeningRecords;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Locale;

/** Sanctions/PEP screening results from verifications. Companies see their own; super admins any company. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/screening")
@Tag(name = "Screening", description = "Sanctions and PEP screening results")
public class ScreeningRecordsController {

    private final ScreeningRecords screeningRecords;

    @GetMapping("/results")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    @Operation(summary = "List screening results", description = "Newest first. Each result links to its verification.")
    public ResponseEntity<ApiResponse<Page<ScreeningRecordDto>>> results(
            @Parameter(description = "NO_HIT, POTENTIAL_MATCH, CONFIRMED_MATCH or SKIPPED") @RequestParam(required = false) String verdict,
            @Parameter(description = "Name, company or verification ID") @RequestParam(required = false) String search,
            @Parameter(description = "Super admins only: one company") @RequestParam(required = false) String companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ScreeningRecordDto> results = screeningRecords.list(company(companyId), upper(verdict), search,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return ok("Screening results retrieved", results);
    }

    @GetMapping("/results/summary")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    @Operation(summary = "Count screening results", description = "Per verdict over the last `days` days (default 30, max 365).")
    public ResponseEntity<ApiResponse<ScreeningSummaryDto>> summary(
            @RequestParam(required = false) String companyId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDateTime since = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 365));
        return ok("Screening summary retrieved", screeningRecords.summary(company(companyId), since));
    }

    /** Super admins: the named company, or all when none is named. Everyone else: their own company. */
    private static String company(String companyId) {
        if (SecurityContextUtil.isSuperAdminAuthenticated()) {
            return companyId == null || companyId.isBlank() ? null : SecurityContextUtil.resolveCompanyId(companyId);
        }
        return SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).responseCode(HttpStatus.OK.value()).responseMessage(message).data(data).build());
    }
}
