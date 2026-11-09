package com.intact_id.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Modulith stores whole events in event_publication. When Hibernate creates that table its text columns
 * are varchar(255), too short for a verification outcome, so every event would fail to save. Widen them at
 * startup, after Hibernate's schema update and before anything publishes (same as
 * 2026-11-07_add_event_publication.sql; nothing happens once they are TEXT).
 */
@Slf4j
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
public class EventPublicationSchema {

    private static final List<String> TEXT_COLUMNS = List.of("serialized_event", "listener_id", "event_type");

    private final JdbcTemplate jdbc;

    @PostConstruct
    void widenTextColumns() {
        List<String> narrow = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'event_publication'
                  AND data_type = 'character varying'""", String.class);
        for (String column : narrow) {
            if (TEXT_COLUMNS.contains(column)) {
                // column comes from the fixed list above, never from input
                jdbc.execute("ALTER TABLE event_publication ALTER COLUMN " + column + " TYPE TEXT");
                log.info("event_publication.{} widened to TEXT", column);
            }
        }
    }
}
