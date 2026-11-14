package com.intact_id.orchestration.web;

import com.intact_id.accounts.SecurityContextUtil;
import com.intact_id.accounts.User;
import com.intact_id.dto.response.ApiResponse;
import com.intact_id.orchestration.monitoring.MonitoredSubject;
import com.intact_id.orchestration.monitoring.Monitoring;
import com.intact_id.orchestration.monitoring.MonitoringAlert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Ongoing monitoring: approved customers screened again on a schedule. Changes become alerts, sent to the company
 * as the screening.updated webhook, and confirmed or dismissed here.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/monitoring")
@PreAuthorize("hasRole('COMPANY') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
@Tag(name = "Ongoing monitoring", description = "Re-screening approved customers, alerts and their review.")
public class MonitoringController {

    private final Monitoring monitoring;

    @GetMapping("/settings")
    @Operation(summary = "Monitoring settings", description = "Whether approved customers are monitored, how often, and which kinds; with counts.")
    public ResponseEntity<ApiResponse<Monitoring.Settings>> settings(@RequestParam(required = false) String companyId) {
        return ok(monitoring.settings(SecurityContextUtil.resolveCompanyId(companyId)));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Change the monitoring settings", description = "frequency: DAILY, WEEKLY or MONTHLY. Applies to verifications approved from now on; start older ones by hand.")
    public ResponseEntity<ApiResponse<Monitoring.Settings>> updateSettings(@RequestParam(required = false) String companyId,
                                                                          @RequestBody Monitoring.Settings changes) {
        return respond(HttpStatus.OK, "Monitoring settings saved", monitoring.updateSettings(SecurityContextUtil.resolveCompanyId(companyId), changes));
    }

    @GetMapping("/subjects")
    @Operation(summary = "Monitored customers", description = "Those with open alerts first. status: ACTIVE or STOPPED; search: name or externalReference.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subjects(@RequestParam(required = false) String companyId,
                                                                     @RequestParam(required = false) MonitoredSubject.Status status,
                                                                     @RequestParam(required = false) String search,
                                                                     @RequestParam(defaultValue = "0") int page,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return ok(pageOf(monitoring.subjects(listScope(companyId), status, search, page, size)));
    }

    @PostMapping("/subjects")
    @Operation(summary = "Start monitoring a verification", description = "An approved verification; for a business, its directors and owners too.")
    public ResponseEntity<ApiResponse<List<MonitoredSubject>>> start(@RequestBody StartRequest request) {
        return respond(HttpStatus.CREATED, "Monitoring started", monitoring.start(scope(), request.verificationId()));
    }

    @GetMapping("/subjects/{id}")
    @Operation(summary = "A monitored customer and their alerts")
    public ResponseEntity<ApiResponse<Monitoring.SubjectDetail>> subject(@PathVariable String id) {
        return ok(monitoring.detail(scope(), id));
    }

    @PostMapping("/subjects/{id}/rescreen")
    @Operation(summary = "Screen again now", description = "Outside the schedule; alerts and the webhook as usual.")
    public ResponseEntity<ApiResponse<Monitoring.SubjectDetail>> rescreen(@PathVariable String id) {
        return respond(HttpStatus.OK, "Screened", monitoring.rescreen(scope(), id));
    }

    @PostMapping("/subjects/{id}/stop")
    @Operation(summary = "Stop monitoring a customer")
    public ResponseEntity<ApiResponse<MonitoredSubject>> stop(@PathVariable String id, @RequestBody(required = false) ReasonRequest request) {
        return respond(HttpStatus.OK, "Monitoring stopped", monitoring.stop(scope(), id, request == null ? null : request.reason()));
    }

    @GetMapping("/verifications/{verificationId}")
    @Operation(summary = "Monitoring of one verification", description = "Its monitored subjects (the person or business, and directors and owners) with their alerts.")
    public ResponseEntity<ApiResponse<List<Monitoring.SubjectDetail>>> forVerification(@PathVariable String verificationId) {
        return ok(monitoring.forVerification(scope(), verificationId));
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alerts", description = "Newest first. status: OPEN (to decide), CONFIRMED, DISMISSED or INFO (a match went away).")
    public ResponseEntity<ApiResponse<Map<String, Object>>> alerts(@RequestParam(required = false) String companyId,
                                                                   @RequestParam(required = false) MonitoringAlert.Status status,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ok(pageOf(monitoring.alerts(listScope(companyId), status, page, size)));
    }

    @PutMapping("/alerts/{id}")
    @Operation(summary = "Decide an alert", description = "decision: CONFIRMED (it is them) or DISMISSED (it is not; those entries are then ignored unless they gain topics).")
    public ResponseEntity<ApiResponse<MonitoringAlert>> resolve(@PathVariable String id, @RequestBody Decision decision) {
        return respond(HttpStatus.OK, "Alert updated", monitoring.resolve(scope(), id, decision.decision(), decision.note(), actor()));
    }

    public record StartRequest(String verificationId) {
    }

    public record ReasonRequest(String reason) {
    }

    public record Decision(MonitoringAlert.Status decision, String note) {
    }

    private static Map<String, Object> pageOf(Page<?> page) {
        return Map.of("content", page.getContent(), "totalElements", page.getTotalElements(), "totalPages", page.getTotalPages());
    }

    private static String scope() {
        return SecurityContextUtil.isSuperAdminAuthenticated() ? null : SecurityContextUtil.resolveCompanyId(null);
    }

    private static String listScope(String companyId) {
        boolean everyCompany = SecurityContextUtil.isSuperAdminAuthenticated() && (companyId == null || companyId.isBlank());
        return everyCompany ? null : SecurityContextUtil.resolveCompanyId(companyId);
    }

    private static String actor() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) return user.getUsername();
        return "api:" + SecurityContextUtil.resolveCompanyId(null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return respond(HttpStatus.OK, "OK", data);
    }

    private static <T> ResponseEntity<ApiResponse<T>> respond(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .responseCode(status.value()).responseMessage(message).success(true).data(data).build());
    }
}
