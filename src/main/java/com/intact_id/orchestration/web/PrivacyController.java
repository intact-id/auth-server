package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.privacy.DataErasure;
import com.intact_id.orchestration.privacy.Retention;
import com.intact_id.orchestration.privacy.RetentionCategory;
import com.intact_id.orchestration.privacy.RetentionRules;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data retention: Intact's limits per country and kind of data, companies' own periods, each verification's
 * retention dates, legal holds, the end of customer relationships, erasure on request and the erasure log.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/privacy")
@Tag(name = "Data retention", description = "Retention by country and kind of data, legal holds, erasure and the erasure log.")
public class PrivacyController {

    private final RetentionRules rules;
    private final Retention retention;

    // ----- Intact's limits per country -----

    @GetMapping("/country-rules")
    @PreAuthorize("hasAuthority('privacy:view')")
    @Operation(summary = "Intact's limits per country", description = "For each country ('*' = any other) and kind of data "
            + "(BIOMETRICS, DOCUMENTS, PERSONAL_DATA, RECORD): minDays, maxDays, defaultDays, startsFrom and legalBasis.")
    public ResponseEntity<ApiResponse<List<RetentionRules.CountryView>>> countryRules() {
        return respond(HttpStatus.OK, "OK", rules.countries());
    }

    @PutMapping("/country-rules/{country}")
    @PreAuthorize("hasAuthority('privacy:manage-platform')")
    @Operation(summary = "Set a country's limits", description = "Replaces that country's limits; a kind of data left out falls back to '*'.")
    public ResponseEntity<ApiResponse<RetentionRules.CountryView>> updateCountry(@PathVariable String country,
                                                                                @RequestBody Map<RetentionCategory, RetentionRules.CountryLimit> limits) {
        return respond(HttpStatus.OK, "Country rules saved", rules.updateCountry(country, limits));
    }

    @DeleteMapping("/country-rules/{country}")
    @PreAuthorize("hasAuthority('privacy:manage-platform')")
    @Operation(summary = "Remove a country's limits", description = "The country then uses the rules for any other country.")
    public ResponseEntity<ApiResponse<Void>> deleteCountry(@PathVariable String country) {
        rules.deleteCountry(country);
        return respond(HttpStatus.OK, "Country rules removed", null);
    }

    // ----- the company's periods -----

    @GetMapping("/company-rules")
    @PreAuthorize("hasAuthority('privacy:view')")
    @Operation(summary = "The company's periods", description = "Per country ('*' = every country): the days the company set, "
            + "and what applies after Intact's limits (effective).")
    public ResponseEntity<ApiResponse<List<RetentionRules.CompanyCountryView>>> companyRules(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "OK", rules.company(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @PutMapping("/company-rules/{country}")
    @PreAuthorize("hasAuthority('privacy:manage')")
    @Operation(summary = "Set the company's periods for a country", description = "Body: days per kind of data, "
            + "e.g. {\"BIOMETRICS\": 30, \"DOCUMENTS\": 365}. A kind left out uses Intact's default. Must be within the country's limits.")
    public ResponseEntity<ApiResponse<List<RetentionRules.CompanyCountryView>>> updateCompany(@PathVariable String country,
                                                                                            @RequestParam(required = false) String companyId,
                                                                                            @RequestBody Map<RetentionCategory, Integer> days) {
        return respond(HttpStatus.OK, "Retention periods saved", rules.updateCompany(SecurityContextUtil.resolveCompanyId(companyId), country, days));
    }

    // ----- one verification -----

    @GetMapping("/verifications/{id}")
    @PreAuthorize("hasAuthority('privacy:view')")
    @Operation(summary = "A verification's retention", description = "Its data's country, legal hold, and for each kind of data "
            + "the period, when it is due, when it went, and how long it must be kept at least.")
    public ResponseEntity<ApiResponse<Retention.Status>> status(@PathVariable String id) {
        return respond(HttpStatus.OK, "OK", retention.status(scope(), id));
    }

    @PostMapping("/verifications/{id}/erase")
    @PreAuthorize("hasAuthority('privacy:requests')")
    @Operation(summary = "Erase data now", description = "scope: BIOMETRICS, DOCUMENTS, PERSONAL_DATA (default) or RECORD, each "
            + "including the ones before it. Refused during a legal hold or a minimum period; super admins may override=true. Cannot be undone.")
    public ResponseEntity<ApiResponse<DataErasure>> erase(@PathVariable String id, @RequestBody EraseRequest request) {
        return respond(HttpStatus.OK, "Data erased", retention.eraseNow(scope(), id, request.scope(), request.reason(), actor(),
                Boolean.TRUE.equals(request.override()), SecurityContextUtil.hasPermission("privacy:override-platform")));
    }

    @PutMapping("/verifications/{id}/hold")
    @PreAuthorize("hasAuthority('privacy:manage')")
    @Operation(summary = "Put on legal hold", description = "Nothing is deleted, by retention or on request, until the hold is released.")
    public ResponseEntity<ApiResponse<Retention.Status>> hold(@PathVariable String id, @RequestBody ReasonRequest request) {
        return respond(HttpStatus.OK, "Legal hold set", retention.hold(scope(), id, request.reason(), actor()));
    }

    @DeleteMapping("/verifications/{id}/hold")
    @PreAuthorize("hasAuthority('privacy:manage')")
    @Operation(summary = "Release the legal hold")
    public ResponseEntity<ApiResponse<Retention.Status>> release(@PathVariable String id) {
        return respond(HttpStatus.OK, "Legal hold released", retention.release(scope(), id, actor()));
    }

    @PostMapping("/verifications/{id}/relationship-ended")
    @PreAuthorize("hasAuthority('privacy:requests')")
    @Operation(summary = "The customer relationship ended", description = "Periods that start at the end of the relationship "
            + "(e.g. AML record keeping) count from endedAt (now when left out).")
    public ResponseEntity<ApiResponse<Retention.Status>> relationshipEnded(@PathVariable String id,
                                                                          @RequestBody(required = false) EndedRequest request) {
        return respond(HttpStatus.OK, "Recorded", retention.relationshipEnded(scope(), id, request == null ? null : request.endedAt()));
    }

    @PostMapping("/customers/{externalReference}/relationship-ended")
    @PreAuthorize("hasAuthority('privacy:manage-company')")
    @Operation(summary = "A customer's relationship ended", description = "Applies to every verification with this externalReference.")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> customerEnded(@PathVariable String externalReference,
                                                                          @RequestBody(required = false) EndedRequest request) {
        int count = retention.customerRelationshipEnded(SecurityContextUtil.resolveCompanyId(null), externalReference,
                request == null ? null : request.endedAt());
        return respond(HttpStatus.OK, "Recorded", Map.of("verifications", count));
    }

    // ----- overview -----

    @GetMapping("/holds")
    @PreAuthorize("hasAuthority('privacy:view')")
    @Operation(summary = "Verifications on legal hold")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> holds(@RequestParam(required = false) String companyId) {
        return respond(HttpStatus.OK, "OK", retention.holds(listScope(companyId)));
    }

    @GetMapping("/erasures")
    @PreAuthorize("hasAuthority('privacy:view')")
    @Operation(summary = "The erasure log", description = "Newest first. Super admins without companyId see every company's.")
    public ResponseEntity<ApiResponse<List<DataErasure>>> erasures(@RequestParam(required = false) String companyId,
                                                                   @RequestParam(defaultValue = "100") int limit) {
        return respond(HttpStatus.OK, "OK", retention.log(listScope(companyId), limit));
    }

    public record EraseRequest(RetentionCategory scope, String reason, Boolean override) {
    }

    public record ReasonRequest(String reason) {
    }

    public record EndedRequest(LocalDateTime endedAt) {
    }

    /** null for super admins (any company), else the caller's company. */
    private static String scope() {
        return SecurityContextUtil.isStaffAuthenticated() ? null : SecurityContextUtil.resolveCompanyId(null);
    }

    private static String listScope(String companyId) {
        boolean everyCompany = SecurityContextUtil.isStaffAuthenticated() && (companyId == null || companyId.isBlank());
        return everyCompany ? null : SecurityContextUtil.resolveCompanyId(companyId);
    }

    private static String actor() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getUsername();
        return "api:" + SecurityContextUtil.resolveCompanyId(null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
