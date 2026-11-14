package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.privacy.DataErasure;
import com.intact_id.orchestration.privacy.DataEraser;
import com.intact_id.orchestration.privacy.RetentionSettings;
import com.intact_id.orchestration.privacy.RetentionSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data retention and erasure: how long applicants' documents and personal details are kept, erasing one
 * applicant's data on request, and the record of every erasure.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/privacy")
@Tag(name = "Data retention", description = "Retention rules, erasure requests and the erasure log.")
public class PrivacyController {

    private final RetentionSettingsService retention;
    private final DataEraser eraser;

    @GetMapping("/retention")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Retention rules", description = "The platform default, and the company's effective rule. Days are counted from when a verification finished; null keeps the data.")
    public ResponseEntity<ApiResponse<Map<String, RetentionSettings>>> get(@RequestParam(required = false) String companyId) {
        String company = companyId == null && SecurityContextUtil.isSuperAdminAuthenticated() ? null : SecurityContextUtil.resolveCompanyId(companyId);
        Map<String, RetentionSettings> body = new LinkedHashMap<>();
        body.put("platformDefault", retention.platformDefault());
        if (company != null) body.put("company", retention.effective(company));
        return respond(HttpStatus.OK, "OK", body);
    }

    @PutMapping("/retention/default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change the platform's retention rule", description = "Applies to every company without its own rule.")
    public ResponseEntity<ApiResponse<RetentionSettings>> updateDefault(@RequestBody RetentionSettings changes) {
        return respond(HttpStatus.OK, "Default updated", retention.update(null, changes));
    }

    @PutMapping("/retention")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Set the company's own retention rule")
    public ResponseEntity<ApiResponse<RetentionSettings>> updateCompany(@RequestParam(required = false) String companyId,
                                                                       @RequestBody RetentionSettings changes) {
        return respond(HttpStatus.OK, "Retention rule saved", retention.update(SecurityContextUtil.resolveCompanyId(companyId), changes));
    }

    @DeleteMapping("/retention")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Go back to the platform's retention rule")
    public ResponseEntity<ApiResponse<RetentionSettings>> clearCompany(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "Company uses the platform rule", retention.clearOverride(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @PostMapping("/verifications/{id}/erase")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Erase an applicant's data",
            description = "Deletes the verification's documents and personal details; the decision and check outcomes stay. "
                    + "Only for finished verifications. Cannot be undone.")
    public ResponseEntity<ApiResponse<DataErasure>> erase(@PathVariable String id, @RequestBody EraseRequest request) {
        String company = SecurityContextUtil.isSuperAdminAuthenticated() ? null : SecurityContextUtil.resolveCompanyId(null);
        return respond(HttpStatus.OK, "Data erased", eraser.eraseNow(company, id, request.reason(), actor()));
    }

    @GetMapping("/erasures")
    @PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "The erasure log", description = "Newest first. Super admins without companyId see every company's.")
    public ResponseEntity<ApiResponse<List<DataErasure>>> erasures(@RequestParam(required = false) String companyId,
                                                                   @RequestParam(defaultValue = "100") int limit) {
        boolean everyCompany = SecurityContextUtil.isSuperAdminAuthenticated() && (companyId == null || companyId.isBlank());
        return respond(HttpStatus.OK, "OK", eraser.log(everyCompany ? null : SecurityContextUtil.resolveCompanyId(companyId), limit));
    }

    public record EraseRequest(String reason) {
    }

    private static String actor() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getUsername();
        return "api:" + SecurityContextUtil.resolveCompanyId(null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
