package com.intact_id.ops.web;

import java.time.LocalDateTime;
import java.util.List;

/** Health checks grouped into buckets of {@code bucketMinutes}, per service. */
public record SystemStatusHistoryDto(LocalDateTime from, LocalDateTime to, int bucketMinutes, List<Series> services) {

    public record Series(String service, String label, List<Point> points) {
    }

    /** uptime: share of checks in the bucket that succeeded, 0–100. */
    public record Point(LocalDateTime at, long checks, Double uptime, Double averageLatencyMs, Double maxLatencyMs) {
    }
}
