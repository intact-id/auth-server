package com.intact_id.ops;

import com.intact_id.ops.web.SystemStatusDto;
import com.intact_id.ops.web.SystemStatusHistoryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads the health checks ServiceHealthMonitor records. */
@Service
@RequiredArgsConstructor
public class ServiceHealth {

    private static final Map<String, String> LABELS = Map.of(
            ServiceHealthMonitor.DATABASE, "Database",
            "face-service", "Face match & liveness",
            "ocr-service", "Document reading (OCR)",
            "screening-service", "Sanctions screening & risk",
            "billing-service", "Billing");

    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceHealthMonitor monitor;

    @Transactional(readOnly = true)
    public SystemStatusDto status() {
        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("day", now.minusHours(24))
                .addValue("month", now.minus(ServiceHealthMonitor.KEEP));

        Map<String, SystemStatusDto.Service> byService = jdbc.query("""
                WITH latest AS (
                    SELECT DISTINCT ON (service) service, up, latency_ms, checked_at, error
                    FROM service_pings
                    WHERE checked_at >= :month
                    ORDER BY service, checked_at DESC
                ), totals AS (
                    SELECT service,
                           100.0 * COUNT(*) FILTER (WHERE up AND checked_at >= :day)
                                 / NULLIF(COUNT(*) FILTER (WHERE checked_at >= :day), 0) AS uptime_24h,
                           100.0 * COUNT(*) FILTER (WHERE up) / COUNT(*) AS uptime_30d,
                           AVG(latency_ms) FILTER (WHERE up AND checked_at >= :day) AS average_24h,
                           percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms)
                               FILTER (WHERE up AND checked_at >= :day) AS p95_24h,
                           MAX(checked_at) FILTER (WHERE NOT up) AS last_down
                    FROM service_pings
                    WHERE checked_at >= :month
                    GROUP BY service
                )
                SELECT l.service, l.up, l.latency_ms, l.checked_at, l.error,
                       t.uptime_24h, t.uptime_30d, t.average_24h, t.p95_24h, t.last_down
                FROM latest l
                JOIN totals t ON t.service = l.service
                """, params, (rs, i) -> new SystemStatusDto.Service(rs.getString("service"), label(rs.getString("service")),
                rs.getBoolean("up"), rs.getLong("latency_ms"), time(rs, "checked_at"), rs.getString("error"),
                number(rs, "uptime_24h"), number(rs, "uptime_30d"), number(rs, "average_24h"), number(rs, "p95_24h"),
                time(rs, "last_down")))
                .stream().collect(Collectors.toMap(SystemStatusDto.Service::service, s -> s));

        // Services not checked yet (just after a deploy) still show, with no data
        List<SystemStatusDto.Service> services = new ArrayList<>();
        for (String service : monitor.services()) {
            services.add(byService.getOrDefault(service, new SystemStatusDto.Service(service, label(service),
                    null, null, null, null, null, null, null, null, null)));
        }
        LocalDateTime checkedAt = services.stream().map(SystemStatusDto.Service::lastCheckedAt)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        boolean allUp = services.stream().allMatch(s -> Boolean.TRUE.equals(s.up()));
        return new SystemStatusDto(checkedAt, allUp, services);
    }

    /** Buckets of 5 minutes up to 6 hours, hourly up to 7 days, daily beyond. */
    @Transactional(readOnly = true)
    public SystemStatusHistoryDto history(int hours) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(hours);
        int bucketMinutes = hours <= 6 ? 5 : hours <= 24 * 7 ? 60 : 24 * 60;

        Map<String, List<SystemStatusHistoryDto.Point>> points = jdbc.query("""
                SELECT p.service,
                       to_timestamp(floor(extract(epoch FROM p.checked_at) / :bucketSeconds) * :bucketSeconds)
                           AT TIME ZONE 'UTC' AS bucket,
                       COUNT(*) AS checks,
                       100.0 * COUNT(*) FILTER (WHERE p.up) / COUNT(*) AS uptime,
                       AVG(p.latency_ms) FILTER (WHERE p.up) AS average_latency,
                       MAX(p.latency_ms) FILTER (WHERE p.up) AS max_latency
                FROM service_pings p
                WHERE p.checked_at >= :from
                GROUP BY 1, 2
                ORDER BY 2
                """, new MapSqlParameterSource().addValue("from", from).addValue("bucketSeconds", bucketMinutes * 60),
                (rs, i) -> Map.entry(rs.getString("service"), new SystemStatusHistoryDto.Point(time(rs, "bucket"),
                        rs.getLong("checks"), number(rs, "uptime"), number(rs, "average_latency"), number(rs, "max_latency"))))
                .stream().collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        List<SystemStatusHistoryDto.Series> series = new ArrayList<>();
        for (String service : monitor.services()) {
            series.add(new SystemStatusHistoryDto.Series(service, label(service), points.getOrDefault(service, List.of())));
        }
        return new SystemStatusHistoryDto(from, to, bucketMinutes, series);
    }

    private static String label(String service) {
        return LABELS.getOrDefault(service, service);
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Double number(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
