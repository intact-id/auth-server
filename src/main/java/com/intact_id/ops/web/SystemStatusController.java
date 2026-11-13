package com.intact_id.ops.web;

import com.intact_id.dto.response.ApiResponse;
import com.intact_id.ops.ServiceHealth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Health of the services behind Intact ID, from a check every minute. Super admins only. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/status")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "System status", description = "Service health checks, latency and uptime (super admins)")
public class SystemStatusController {

    private final ServiceHealth health;

    @GetMapping
    @Operation(summary = "Current status", description = "Latest check of each service, with 24-hour and 30-day uptime and latency.")
    public ResponseEntity<ApiResponse<SystemStatusDto>> status() {
        return ok("System status retrieved", health.status());
    }

    @GetMapping("/history")
    @Operation(summary = "Latency and uptime over time", description = "The last `hours` hours (default 24, max 720), in buckets.")
    public ResponseEntity<ApiResponse<SystemStatusHistoryDto>> history(@RequestParam(defaultValue = "24") int hours) {
        return ok("System status history retrieved", health.history(Math.min(Math.max(hours, 1), 720)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String message, T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).responseCode(HttpStatus.OK.value()).responseMessage(message).data(data).build());
    }
}
