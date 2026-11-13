package com.intact_id.orchestration.verification;

import com.intact_id.orchestration.web.TurnaroundStatsDto;
import com.intact_id.orchestration.web.VerificationStatsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Counts and turnaround times for the overview and analytics pages, read straight from verification_requests
 * and verification_steps. A verification belongs to the day it was submitted.
 */
@Service
@RequiredArgsConstructor
public class VerificationStats {

    private static final String FILTERS = """
            WHERE (CAST(:companyId AS text) IS NULL OR v.company_id = :companyId)
              AND (CAST(:environment AS text) IS NULL OR v.environment = :environment)
              AND v.created_at >= :from AND v.created_at < :to
            """;

    private static final String COUNTS = """
            SELECT COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE v.overall_decision = 'approved') AS approved,
                   COUNT(*) FILTER (WHERE v.overall_decision = 'rejected' OR v.status = 'REJECTED') AS rejected,
                   COUNT(*) FILTER (WHERE v.status = 'PENDING_APPROVAL') AS in_review,
                   COUNT(*) FILTER (WHERE v.status IN ('SUBMITTED', 'PENDING', 'PROCESSING', 'ON_HOLD')) AS in_progress,
                   COUNT(*) FILTER (WHERE v.status = 'FAILED') AS failed
            FROM verification_requests v
            """ + FILTERS;

    private static final String SECONDS_TO_FINISH = "EXTRACT(EPOCH FROM (v.completed_at - v.created_at))";

    private final NamedParameterJdbcTemplate jdbc;

    /** companyId and environment null mean all. */
    @Transactional(readOnly = true)
    public VerificationStatsDto verifications(String companyId, String environment, int days) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);
        MapSqlParameterSource params = params(companyId, environment, from, to);

        VerificationStatsDto.Counts current = jdbc.queryForObject(COUNTS, params, VerificationStats::counts);
        VerificationStatsDto.Counts previous = jdbc.queryForObject(COUNTS,
                params(companyId, environment, from.minusDays(days), from), VerificationStats::counts);

        Map<LocalDate, VerificationStatsDto.Day> byDate = jdbc.query("""
                SELECT CAST(v.created_at AS date) AS day,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE v.overall_decision = 'approved') AS approved,
                       COUNT(*) FILTER (WHERE v.overall_decision = 'rejected' OR v.status = 'REJECTED') AS rejected,
                       COUNT(*) FILTER (WHERE v.status = 'PENDING_APPROVAL') AS in_review
                FROM verification_requests v
                """ + FILTERS + " GROUP BY 1", params, (rs, i) -> new VerificationStatsDto.Day(
                rs.getObject("day", LocalDate.class), rs.getLong("total"), rs.getLong("approved"),
                rs.getLong("rejected"), rs.getLong("in_review")))
                .stream().collect(Collectors.toMap(VerificationStatsDto.Day::date, Function.identity()));
        List<VerificationStatsDto.Day> daily = everyDay(from, to, date ->
                byDate.getOrDefault(date, new VerificationStatsDto.Day(date, 0, 0, 0, 0)));

        List<VerificationStatsDto.Slice> byType = jdbc.query("""
                SELECT LOWER(COALESCE(NULLIF(v.verification_type, ''), 'individual')) AS label, COUNT(*) AS count
                FROM verification_requests v
                """ + FILTERS + " GROUP BY 1 ORDER BY 2 DESC", params,
                (rs, i) -> new VerificationStatsDto.Slice(rs.getString("label"), rs.getLong("count")));

        List<VerificationStatsDto.CountrySlice> byCountry = jdbc.query("""
                SELECT COALESCE(NULLIF(UPPER(v.customer_data->>'countryOfResidence'), ''),
                                NULLIF(UPPER(v.customer_data->>'nationality'), ''), 'UNKNOWN') AS country,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE v.overall_decision = 'approved') AS approved,
                       COUNT(*) FILTER (WHERE v.overall_decision = 'rejected' OR v.status = 'REJECTED') AS rejected
                FROM verification_requests v
                """ + FILTERS + " GROUP BY 1 ORDER BY 2 DESC LIMIT 8", params,
                (rs, i) -> new VerificationStatsDto.CountrySlice(rs.getString("country"), rs.getLong("total"),
                        rs.getLong("approved"), rs.getLong("rejected")));

        // Step statuses: COMPLETED = passed, PENDING_APPROVAL = review, FAILED = failed or could not run
        List<VerificationStatsDto.CheckSlice> checks = jdbc.query("""
                SELECT s.step_name AS step,
                       COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE s.status = 'COMPLETED') AS passed,
                       COUNT(*) FILTER (WHERE s.status = 'PENDING_APPROVAL') AS review,
                       COUNT(*) FILTER (WHERE s.status = 'FAILED') AS failed
                FROM verification_steps s
                JOIN verification_requests v ON v.id = s.verification_id
                """ + FILTERS + " GROUP BY 1 ORDER BY 2 DESC", params,
                (rs, i) -> new VerificationStatsDto.CheckSlice(rs.getString("step"), rs.getLong("total"),
                        rs.getLong("passed"), rs.getLong("review"), rs.getLong("failed")));

        return new VerificationStatsDto(from, to, current, previous, daily, byType, byCountry, checks);
    }

    /** companyId and environment null mean all. */
    @Transactional(readOnly = true)
    public TurnaroundStatsDto turnaround(String companyId, String environment, int days) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(days);
        MapSqlParameterSource params = params(companyId, environment, from, to);

        // The automated checks end when their last step was written
        TurnaroundStatsDto summary = jdbc.queryForObject("""
                SELECT COUNT(v.completed_at) AS finished,
                       COUNT(v.reviewed_at) AS reviewed,
                       AVG(%1$s) AS average,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY %1$s) AS median,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY %1$s) AS p90,
                       AVG(%1$s) FILTER (WHERE v.reviewed_at IS NULL) AS automated_average,
                       AVG(%1$s) FILTER (WHERE v.reviewed_at IS NOT NULL) AS reviewed_average,
                       AVG(EXTRACT(EPOCH FROM (v.processing_started_at - v.created_at))) AS queued,
                       AVG(EXTRACT(EPOCH FROM (steps.done - COALESCE(v.processing_started_at, v.created_at)))) AS checks,
                       AVG(EXTRACT(EPOCH FROM (COALESCE(v.review_assigned_at, v.reviewed_at) - v.review_requested_at))) AS waiting,
                       AVG(EXTRACT(EPOCH FROM (v.reviewed_at - v.review_assigned_at))) AS reviewing
                FROM verification_requests v
                LEFT JOIN LATERAL (SELECT MAX(s.executed_at) AS done FROM verification_steps s
                                   WHERE s.verification_id = v.id) steps ON TRUE
                """.formatted(SECONDS_TO_FINISH) + FILTERS, params, (rs, i) -> new TurnaroundStatsDto(
                from, to, rs.getLong("finished"), rs.getLong("reviewed"),
                number(rs, "average"), number(rs, "median"), number(rs, "p90"),
                number(rs, "automated_average"), number(rs, "reviewed_average"),
                new TurnaroundStatsDto.Stages(number(rs, "queued"), number(rs, "checks"),
                        number(rs, "waiting"), number(rs, "reviewing")),
                List.of(), List.of()));

        List<TurnaroundStatsDto.CheckTime> checks = jdbc.query("""
                SELECT s.step_name AS step,
                       COUNT(*) AS runs,
                       AVG(s.duration_ms) AS average,
                       percentile_cont(0.9) WITHIN GROUP (ORDER BY s.duration_ms) AS p90
                FROM verification_steps s
                JOIN verification_requests v ON v.id = s.verification_id
                """ + FILTERS + " AND s.duration_ms IS NOT NULL GROUP BY 1 ORDER BY 3 DESC", params,
                (rs, i) -> new TurnaroundStatsDto.CheckTime(rs.getString("step"), rs.getLong("runs"),
                        number(rs, "average"), number(rs, "p90")));

        Map<LocalDate, TurnaroundStatsDto.Day> byDate = jdbc.query("""
                SELECT CAST(v.created_at AS date) AS day,
                       COUNT(v.completed_at) AS finished,
                       AVG(%1$s) AS average,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY %1$s) AS median
                FROM verification_requests v
                """.formatted(SECONDS_TO_FINISH) + FILTERS + " GROUP BY 1", params,
                (rs, i) -> new TurnaroundStatsDto.Day(rs.getObject("day", LocalDate.class), rs.getLong("finished"),
                        number(rs, "average"), number(rs, "median")))
                .stream().collect(Collectors.toMap(TurnaroundStatsDto.Day::date, Function.identity()));
        List<TurnaroundStatsDto.Day> daily = everyDay(from, to, date ->
                byDate.getOrDefault(date, new TurnaroundStatsDto.Day(date, 0, null, null)));

        return new TurnaroundStatsDto(from, to, summary.finished(), summary.reviewed(), summary.averageSeconds(),
                summary.medianSeconds(), summary.p90Seconds(), summary.automatedAverageSeconds(),
                summary.reviewedAverageSeconds(), summary.stages(), checks, daily);
    }

    private static MapSqlParameterSource params(String companyId, String environment, LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("environment", environment)
                .addValue("from", from)
                .addValue("to", to);
    }

    private static VerificationStatsDto.Counts counts(ResultSet rs, int row) throws SQLException {
        long approved = rs.getLong("approved");
        long rejected = rs.getLong("rejected");
        Double passRate = approved + rejected == 0 ? null : Math.round(1000.0 * approved / (approved + rejected)) / 10.0;
        return new VerificationStatsDto.Counts(rs.getLong("total"), approved, rejected, rs.getLong("in_review"),
                rs.getLong("in_progress"), rs.getLong("failed"), passRate);
    }

    /** One entry per calendar day from {@code from} to {@code to}, both included. */
    private static <T> List<T> everyDay(LocalDateTime from, LocalDateTime to, Function<LocalDate, T> entry) {
        List<T> days = new ArrayList<>();
        for (LocalDate date = from.toLocalDate(); !date.isAfter(to.toLocalDate()); date = date.plusDays(1)) {
            days.add(entry.apply(date));
        }
        return days;
    }

    private static Double number(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
