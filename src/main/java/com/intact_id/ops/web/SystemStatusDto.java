package com.intact_id.ops.web;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The latest health check of each service and its record over the last 24 hours and 30 days.
 * Uptime is the share of checks that succeeded, 0–100; null when there are no checks in that window.
 */
public record SystemStatusDto(LocalDateTime checkedAt, boolean allUp, List<Service> services) {

    public record Service(String service, String label, Boolean up, Long latencyMs, LocalDateTime lastCheckedAt,
                          String error, Double uptime24h, Double uptime30d, Double averageLatencyMs24h,
                          Double p95LatencyMs24h, LocalDateTime lastDownAt) {
    }
}
