package com.intact_id.orchestration.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intact_id.orchestration.web.ScreeningRecordDto;
import com.intact_id.orchestration.web.ScreeningSummaryDto;
import com.intact_id.util.exception.ApplicationExceptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sanctions and PEP screening results, read from the "sanctions_screening" step every screened
 * verification stores. Nothing is copied: the verification step is the record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningRecords {

    /** NO_HIT / POTENTIAL_MATCH / CONFIRMED_MATCH come from sactions-service; SKIPPED means screening could not run. */
    public static final Set<String> VERDICTS = Set.of("NO_HIT", "POTENTIAL_MATCH", "CONFIRMED_MATCH", "SKIPPED");

    private static final String FROM_AND_FILTERS = """
            FROM verification_steps s
            JOIN verification_requests v ON v.id = s.verification_id
            LEFT JOIN company_tbl c ON c.id = v.company_id
            WHERE s.step_name = 'sanctions_screening'
              AND (CAST(:companyId AS text) IS NULL OR v.company_id = :companyId)
              AND (CAST(:since AS timestamp) IS NULL OR COALESCE(s.executed_at, s.created_at) >= :since)
            """;

    private static final String VERDICT_FILTER = """
              AND (CAST(:verdict AS text) IS NULL
                   OR (:verdict = 'SKIPPED' AND s.result->>'verdict' IS NULL)
                   OR s.result->>'verdict' = :verdict)
              AND (CAST(:search AS text) IS NULL
                   OR LOWER(v.id) LIKE :search
                   OR LOWER(COALESCE(c.legal_name, '')) LIKE :search
                   OR LOWER(COALESCE(v.customer_data::text, '')) LIKE :search)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** Newest first. companyId null means every company (super admins only; the caller decides). */
    @Transactional(readOnly = true)
    public Page<ScreeningRecordDto> list(String companyId, String verdict, String search, Pageable pageable) {
        if (verdict != null && !VERDICTS.contains(verdict)) {
            throw new ApplicationExceptions.IntactBadRequestException("verdict must be one of " + VERDICTS);
        }
        MapSqlParameterSource params = filters(companyId, null)
                .addValue("verdict", verdict)
                .addValue("search", search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%")
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM_AND_FILTERS + VERDICT_FILTER, params, Long.class);
        List<ScreeningRecordDto> rows = jdbc.query("""
                SELECT s.id, s.verification_id, s.status AS step_status, s.result::text AS result,
                       COALESCE(s.executed_at, s.created_at) AS checked_at,
                       v.verification_type, v.status AS verification_status, v.overall_decision,
                       v.customer_data::text AS customer_data, v.company_id, c.legal_name AS company_name
                """ + FROM_AND_FILTERS + VERDICT_FILTER + """
                ORDER BY COALESCE(s.executed_at, s.created_at) DESC
                LIMIT :limit OFFSET :offset
                """, params, this::toRecord);
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    /** Counts per verdict, plus PEP and adverse-media hits, since the given time. */
    @Transactional(readOnly = true)
    public ScreeningSummaryDto summary(String companyId, LocalDateTime since) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       COUNT(*) FILTER (WHERE s.result->>'verdict' = 'NO_HIT') AS no_hit,
                       COUNT(*) FILTER (WHERE s.result->>'verdict' = 'POTENTIAL_MATCH') AS potential_match,
                       COUNT(*) FILTER (WHERE s.result->>'verdict' = 'CONFIRMED_MATCH') AS confirmed_match,
                       COUNT(*) FILTER (WHERE s.result->>'verdict' IS NULL) AS skipped,
                       COUNT(*) FILTER (WHERE s.result->>'pep' = 'true') AS pep,
                       COUNT(*) FILTER (WHERE s.result->>'adverse_media' = 'true') AS adverse_media
                """ + FROM_AND_FILTERS, filters(companyId, since), (rs, i) -> new ScreeningSummaryDto(
                since, rs.getLong("total"), rs.getLong("no_hit"), rs.getLong("potential_match"),
                rs.getLong("confirmed_match"), rs.getLong("skipped"), rs.getLong("pep"), rs.getLong("adverse_media")));
    }

    private static MapSqlParameterSource filters(String companyId, LocalDateTime since) {
        return new MapSqlParameterSource()
                .addValue("companyId", companyId)
                .addValue("since", since == null ? null : Timestamp.valueOf(since));
    }

    private ScreeningRecordDto toRecord(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> result = json(rs.getString("result"));
        Map<String, Object> customer = json(rs.getString("customer_data"));
        Timestamp checkedAt = rs.getTimestamp("checked_at");
        Object matches = result.get("matches");
        return new ScreeningRecordDto(
                rs.getString("id"),
                rs.getString("verification_id"),
                rs.getString("company_id"),
                rs.getString("company_name"),
                subjectName(customer),
                rs.getString("verification_type"),
                result.get("verdict") == null ? "SKIPPED" : String.valueOf(result.get("verdict")),
                result.get("hits") instanceof Number n ? n.intValue() : null,
                result.get("top_score") instanceof Number n ? n.doubleValue() : null,
                Boolean.TRUE.equals(result.get("pep")),
                Boolean.TRUE.equals(result.get("adverse_media")),
                matches instanceof List<?> list ? (List<?>) list : List.of(),
                result.get("verdict") == null ? (String) result.get("reason") : null,
                rs.getString("verification_status"),
                rs.getString("overall_decision"),
                checkedAt == null ? null : checkedAt.toLocalDateTime());
    }

    private static String subjectName(Map<String, Object> customer) {
        Object first = customer.get("firstName");
        Object last = customer.get("lastName");
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (!name.isEmpty()) return name;
        Object business = customer.getOrDefault("companyName", customer.get("businessName"));
        return business == null ? null : String.valueOf(business);
    }

    private Map<String, Object> json(String text) {
        if (text == null || text.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(text, new TypeReference<>() { });
        } catch (Exception e) {
            log.warn("Unreadable JSON on a screening record: {}", e.getMessage());
            return Map.of();
        }
    }
}
