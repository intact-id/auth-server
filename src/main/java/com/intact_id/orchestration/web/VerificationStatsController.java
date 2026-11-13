package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.verification.VerificationStats;
import com.intact_id.orchestration.verification.VerificationTimeline;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Verification counts for the overview and analytics pages, and turnaround times (TAT). Companies see their
 * own counts; turnaround times are for Intact staff (super admins) only.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "Verification stats", description = "Verification counts and turnaround times")
public class VerificationStatsController {

    private final VerificationStats stats;
    private final VerificationTimeline timeline;

    @GetMapping("/stats/verifications")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ADMIN') or hasRole('COMPANY')")
    @Operation(summary = "Verification counts", description = "Totals, the previous period's totals, a daily series and "
            + "breakdowns by type, country and check, for verifications submitted in the last `days` days (default 30, max 365).")
    public ResponseEntity<ApiResponse<VerificationStatsDto>> verifications(
            @Parameter(description = "Super admins only: one company") @RequestParam(required = false) String companyId,
            @RequestParam(defaultValue = "30") int days) {
        return ok("Verification stats retrieved", stats.verifications(company(companyId), clampDays(days)));
    }

    @GetMapping("/stats/turnaround")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Turnaround times", description = "Super admins only. Average, median and 90th-percentile time "
            + "from submission to decision, the average per stage and per check, and a daily series.")
    public ResponseEntity<ApiResponse<TurnaroundStatsDto>> turnaround(
            @Parameter(description = "One company; all when omitted") @RequestParam(required = false) String companyId,
            @RequestParam(defaultValue = "30") int days) {
        return ok("Turnaround stats retrieved", stats.turnaround(company(companyId), clampDays(days)));
    }

    @GetMapping("/kyc/verifications/{verificationId}/timeline")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "A verification's turnaround time by stage", description = "Super admins only. Queued, automated "
            + "checks (with each check's time), waiting for a reviewer and under review.")
    public ResponseEntity<ApiResponse<VerificationTimelineDto>> timeline(@PathVariable String verificationId) {
        return ok("Verification timeline retrieved", timeline.of(verificationId));
    }

    /** Super admins: the named company, or all when none is named. Everyone else: their own company. */
    private static String company(String companyId) {
        if (SecurityContextUtil.isSuperAdminAuthenticated()) {
            return companyId == null || companyId.isBlank() ? null : SecurityContextUtil.resolveCompanyId(companyId);
        }
        return SecurityContextUtil.getAuthenticatedCompanyFromAny().getId();
    }

    private static int clampDays(int days) {
        return Math.min(Math.max(days, 1), 365);
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).responseCode(HttpStatus.OK.value()).responseMessage(message).data(data).build());
    }
}
